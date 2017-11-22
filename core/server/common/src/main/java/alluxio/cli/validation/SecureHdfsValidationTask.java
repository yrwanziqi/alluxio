/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.cli.validation;

import alluxio.Configuration;
import alluxio.PropertyKey;
import alluxio.cli.ValidateEnv;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task for validating security configurations.
 */
public class SecureHdfsValidationTask implements ValidationTask {
  /** Name of the environment variable to store the path to Hadoop config directory */
  private static final String HADOOP_CONF_DIR_ENV_VAR = "HADOOP_CONF_DIR";

  /**
   * Regular expression to parse principal used by Alluxio to connect to secure
   * HDFS.
   *
   * @see <a href="https://web.mit.edu/kerberos/krb5-1.5/krb5-1.5.4/doc/krb5-user/What-is-a-Kerberos-Principal_003f.html">Kerberos documentation</a>
   * for more details.
   */
  private static final Pattern PRINCIPAL_PATTERN =
      Pattern.compile("(?<primary>[\\w][\\w-]*\\$?)(/(?<instance>[\\w]+))?(@(?<realm>[\\w]+))?");

  private final String mProcess;

  /**
   * Constructor of {@link SecureHdfsValidationTask}.
   *
   * @param process type of the process on behalf of which this validation task is run
   */
  public SecureHdfsValidationTask(String process) {
    mProcess = process.toLowerCase();
  }

  @Override
  public boolean validate(String[] args, Map<String, String> optionsMap) {
    if (!validatePrincipalLogin()) {
      System.err.format("Principal login test failed.");
      return false;
    }
    if (!validateHdfsSettingParity(optionsMap)) {
      System.err.format("Hdfs setting do not match.");
      return false;
    }
    return true;
  }

  private boolean validatePrincipalLogin() {
    // Check whether can login with specified principal and keytab
    String principal = "";
    String keytab = "";
    String primary;
    String instance;
    String realm;
    if (mProcess.equals("master")) {
      principal = Configuration.get(PropertyKey.MASTER_PRINCIPAL);
      keytab = Configuration.get(PropertyKey.MASTER_KEYTAB_KEY_FILE);
    } else if (mProcess.equals("worker")) {
      principal = Configuration.get(PropertyKey.WORKER_PRINCIPAL);
      keytab = Configuration.get(PropertyKey.WORKER_KEYTAB_FILE);
    }
    Matcher matchPrincipal = PRINCIPAL_PATTERN.matcher(principal);
    if (!matchPrincipal.matches()) {
      System.err.format("Principal %s is not in the right format.%n", principal);
      return false;
    }
    primary = matchPrincipal.group("primary");
    instance = matchPrincipal.group("instance");
    realm = matchPrincipal.group("realm");

    // Login with principal and keytab
    int exitVal =
        Utils.getResultFromProcess(new String[] {"kinit", "-kt", keytab, principal}).getExitValue();
    if (exitVal != 0) {
      System.err.format("Kerberos login failed for %s with keytab %s with exit value %d.%n",
          principal, keytab, exitVal);
      System.err.format("Primary is %s, instance is %s and realm is %s.%n",
          primary, instance, realm);
      return false;
    }
    return true;
  }

  private boolean validateHdfsSettingParity(Map<String, String> optionsMap) {
    String serverHadoopConfDirPath;
    if (optionsMap.containsKey(ValidateEnv.HADOOP_CONF_DIR_OPTION.getOpt())) {
      serverHadoopConfDirPath = optionsMap.get(ValidateEnv.HADOOP_CONF_DIR_OPTION.getOpt());
    } else {
      serverHadoopConfDirPath = System.getenv(HADOOP_CONF_DIR_ENV_VAR);
    }
    if (serverHadoopConfDirPath == null) {
      System.out.println("Path to server-side hadoop configuration unspecified,"
          + " aborting validation for HDFS properties.");
      return false;
    }
    String serverCoreSiteFilePath = serverHadoopConfDirPath + "/core-site.xml";
    String serverHdfsSiteFilePath = serverHadoopConfDirPath + "/hdfs-site.xml";

    // If Configuration does not contain the key, then a {@link RuntimeException} will be thrown
    // before calling the {@link String#split} method.
    String[] clientHaoopConfFilePaths = Configuration.get(PropertyKey.UNDERFS_HDFS_CONFIGURATION).split(":");
    String clientCoreSiteFilePath = clientHaoopConfFilePaths[0];
    String clientHdfsSiteFilePath = clientHaoopConfFilePaths[1];
    if (clientCoreSiteFilePath == null || clientCoreSiteFilePath.isEmpty()
        || clientHdfsSiteFilePath == null || clientHdfsSiteFilePath.isEmpty()) {
      System.out.println("Cannot locate the client-side hadoop configurations,"
          + " aborting validation for HDFS properties.");
      return false;
    }
    ConfigurationFileParser parser = new ConfigurationFileParser();
    Map<String, String> serverCoreSiteProps = parser.parseXmlConfiguration(serverCoreSiteFilePath);
    Map<String, String> clientCoreSiteProps = parser.parseXmlConfiguration(clientCoreSiteFilePath);
    boolean matches = true;
    for (Map.Entry<String, String> prop : clientCoreSiteProps.entrySet()) {
      if (!serverCoreSiteProps.containsKey(prop.getKey())
          || !prop.getValue().equals(serverCoreSiteProps.get(prop.getKey()))) {
        matches = false;
        System.err.format("For %s, client has %s, but server has %s.%n",
            prop.getKey(), prop.getValue(), serverCoreSiteProps.get(prop.getKey()));
      }
    }
    return matches;
  }
}
