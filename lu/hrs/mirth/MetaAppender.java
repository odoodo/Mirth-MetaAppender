package lu.hrs.mirth;

// should extend
public class MetaAppender {

	static MetaAppenderBase metaAppender = null;

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderBase activate() {
		return getInstance(null, null, null, null, null);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderBase activate(String customLogPath) {
		return getInstance(customLogPath, null, null, null, null);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @param customMaxFileSize
	 *            Defines a custom maximal size per log file (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderBase activate(String customLogPath, String customMaxFileSize) {
		return getInstance(customLogPath, customMaxFileSize, null, null, null);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @param customMaxFileSize
	 *            Defines a custom maximal size per log file (OPTIONAL)
	 * @param customMaxBackupIndex
	 *            Defines a maximum number of log files that will be created per channel till the oldest is overwritten (round-robin) (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderBase activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex) {
		return getInstance(customLogPath, customMaxFileSize, customMaxBackupIndex, null, null);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @param customMaxFileSize
	 *            Defines a custom maximal size per log file (OPTIONAL)
	 * @param customMaxBackupIndex
	 *            Defines a maximum number of log files that will be created per channel till the oldest is overwritten (round-robin) (OPTIONAL)
	 * @param customLogPattern
	 *            Defines a custom structure for the log file entries (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderBase activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern) {
		return getInstance(customLogPath, customMaxFileSize, customMaxBackupIndex, customLogPattern, null);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @param customMaxFileSize
	 *            Defines a custom maximal size per log file (OPTIONAL)
	 * @param customMaxBackupIndex
	 *            Defines a maximum number of log files that will be created per channel till the oldest is overwritten (round-robin) (OPTIONAL)
	 * @param customLogPattern
	 *            Defines a custom structure for the log file entries (OPTIONAL)
	 * @param logAllToMainLog
	 *            If this flag is set, all log entries (also channel-specific ones) will also be logged to the main log. (off by default) (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderBase activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern,
			Boolean logAllToMainLog) {
		return getInstance(customLogPath, customMaxFileSize, customMaxBackupIndex, customLogPattern, logAllToMainLog);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once).<br/>
	 * <br/>
	 * The right implementation is chosen automatically dependent on the present log4j version
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @param customMaxFileSize
	 *            Defines a custom maximal size per log file (OPTIONAL)
	 * @param customMaxBackupIndex
	 *            Defines a maximum number of log files that will be created per channel till the oldest is overwritten (round-robin) (OPTIONAL)
	 * @param customLogPattern
	 *            Defines a custom structure for the log file entries (OPTIONAL)
	 * @param logAllToMainLog
	 *            If this flag is set, all log entries (also channel-specific ones) will also be logged to the main log. (off by default) (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	private static MetaAppenderBase getInstance(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern,
			Boolean logAllToMainLog) {

		if (MetaAppender.metaAppender != null) {
			return MetaAppender.metaAppender;
		}

		// assume that the current log4j version is 2.x
		boolean log4Jv2 = true;
		try {
			// check if v2.x classes are present
			Class.forName("org.apache.logging.log4j.core.appender.RollingFileAppender");
		} catch (ClassNotFoundException e) {
			// nope - so it's still v1.x
			log4Jv2 = false;
		}
		return (MetaAppenderBase) (log4Jv2
				? MetaAppenderLog4J2.activate(customLogPath, customMaxFileSize, customMaxBackupIndex, customLogPattern, logAllToMainLog)
				: MetaAppenderLog4J1.activate(customLogPath, customMaxFileSize, customMaxBackupIndex, customLogPattern, logAllToMainLog));
	}
}
