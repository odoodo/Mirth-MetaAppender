package lu.hrs.mirth;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;

import com.mirth.connect.plugins.serverlog.ArrayAppender;
import com.mirth.connect.server.userutil.ChannelUtil;

/**
 * This custom appender hijacks the Mirth logging mechanism and reroutes channel-specific logging to a log file named like the channel causing the log
 * event. None channel-specific log messages are still routed to the main log file. Further, the channel name is added to all channel specific log
 * entries for the main log and all logs to the console or dashboard.
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You
 * can obtain one at https://mozilla.org/MPL/2.0/.
 * 
 * @author ortwin.donak
 * 
 */
public class MetaAppenderLog4J2 extends AbstractAppender implements MetaAppenderBase {

	private static final Pattern patternUuid = Pattern.compile("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})");
	private static final String mainLogAppenderName = "mirth";
	private static final String errorAppenderName = "mirthErrors";
	private static final String consoleAppenderName = "console";
	private static String mirthArrayAppenderName = "mirthDashboard";
	private static MetaAppenderLog4J2 metaAppender = null;
	private final HashMap<String, Appender> appenders = new HashMap<String, Appender>();
	private Long configMaxFileSize = null;
	private Integer configMaxBackupIndex = null;
	private String configLayout = null;
	private String configLogLocation = null;
	private boolean logAllToMainLog = false;
	private String focusedChannelName = null;
	private String filteredChannelName = null;

	/**
	 * Limits the console and Mirth dashboard log to the output of one specific channel. The output of the log files is not influenced.<br/>
	 * <br/>
	 * <i>A channel can be focused even before it has been deployed. However focusing will only become active when channel is actually deployed.</i>
	 * 
	 * @param Identifier
	 *            The id or name of the channel that should be focused.
	 * @return The name of the channel that has been focused
	 */
	public static String setFocus(String identifier) {
		// check parameter
		if ((identifier == null) || identifier.isEmpty()) {
			return null;
		}

		// normalize
		identifier = identifier.trim();
		// get the current instance of the appender or create it if not yet existing
		MetaAppenderLog4J2 appender = getInstance(null, null, null, null, null);
		// resolve the channel id to the channel name, if the provided identifier is a channel name
		String channelName = ChannelUtil.getChannelName(identifier);
		// determine the name of the channel that should be focused and set it as focused channel.
		appender.focusedChannelName = (channelName != null) ? channelName : identifier;

		// assure that a channel is not focused and filtered at the same time
		if (appender.focusedChannelName.equals(appender.filteredChannelName)) {
			appender.filteredChannelName = null;
		}

		// indicate the name of the focused channel to the user
		return appender.focusedChannelName;
	}

	/**
	 * Removes the focus onto the output of a specific channel and shows output of all channels on the mirth dashboard and console log.
	 * 
	 * @return The name of the channel for which the focus has been removed
	 */
	public static String removeFocus() {
		// get the current instance of the appender or create it if not yet existing
		MetaAppenderLog4J2 appender = getInstance(null, null, null, null, null);
		// read the name of the currently focused channel
		String focusedChannel = appender.focusedChannelName;
		// remove focus
		appender.focusedChannelName = null;

		// and indicate the name of the channel from which the focus has been removed to the user
		return focusedChannel;
	}

	/**
	 * Suppresses the logging of a specific channel at the console and Mirth dashboard. The output of the log files is not influenced.<br/>
	 * <br/>
	 * <i>A channel can be filtered even before it has been deployed. However filtering will only become active when channel is actually deployed.</i>
	 * 
	 * @param Identifier
	 *            The id or name of the channel that should be filtered.
	 * @return The name of the channel that has been filtered
	 */
	public static String setFilter(String identifier) {
		// check parameter
		if ((identifier == null) || identifier.isEmpty()) {
			return null;
		}

		// normalize
		identifier = identifier.trim();
		// get the current instance of the appender or create it if not yet existing
		MetaAppenderLog4J2 appender = getInstance(null, null, null, null, null);
		// resolve the channel id to the channel name, if the provided identifier is a channel name
		String channelName = ChannelUtil.getChannelName(identifier);
		// determine the name of the channel that should be filtered and set it as filtered channel.
		appender.filteredChannelName = (channelName != null) ? channelName : identifier;

		// assure that a channel is not focused and filtered at the same time
		if (appender.filteredChannelName.equals(appender.focusedChannelName)) {
			appender.focusedChannelName = null;
		}

		// indicate the name of the filtered channel to the user
		return appender.filteredChannelName;
	}

	/**
	 * Removes the filter onto the output of a specific channel and shows output of all channels on the mirth dashboard and console log.
	 * 
	 * @return The name of the channel for which the filter has been removed
	 */
	public static String removeFilter() {
		// get the current instance of the appender or create it if not yet existing
		MetaAppenderLog4J2 appender = getInstance(null, null, null, null, null);
		// read the name of the currently filtered channel
		String filteredChannel = appender.filteredChannelName;
		// remove filter
		appender.filteredChannelName = null;

		// and indicate the name of the channel from which the filter has been removed to the user
		return filteredChannel;
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderLog4J2 activate() {
		return getInstance(null, null, null, null, null);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppenderLog4J2 activate(String customLogPath) {
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
	public static MetaAppenderLog4J2 activate(String customLogPath, String customMaxFileSize) {
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
	public static MetaAppenderLog4J2 activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex) {
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
	public static MetaAppenderLog4J2 activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern) {
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
	public static MetaAppenderLog4J2 activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern,
			Boolean logAllToMainLog) {
		return getInstance(customLogPath, customMaxFileSize, customMaxBackupIndex, customLogPattern, logAllToMainLog);
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
	private static MetaAppenderLog4J2 getInstance(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex,
			String customLogPattern, Boolean logAllToMainLog) {

		return (MetaAppenderLog4J2.metaAppender != null) ? MetaAppenderLog4J2.metaAppender
				: new MetaAppenderLog4J2(customLogPath, customMaxFileSize, customMaxBackupIndex, customLogPattern, logAllToMainLog);
	}

	/**
	 * Creates a custom rolling file appender that logs channel-specific messages to a log file named like the channel. Channel-independent logging
	 * will be logged to standard mirth log. Logging attributes are taken from the log4j.properties configuration by default but can be overwritten
	 * when calling the constructor.
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
	 */
	@SuppressWarnings("unchecked")
	private MetaAppenderLog4J2(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern,
			Boolean logAllToMainLog) {

		super("MetaAppender", null, null, false, null);
		Logger root = null;
		// set 24h date format
		com.mirth.connect.plugins.serverlog.ServerLogItem.DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

		// get the logger context managing the list of loggers, appenders and their configuration
		LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);

		// get root logger
		root = loggerContext.getRootLogger();

		root.error("META APPENDER Log4Jv2");
		// obtain the configuration of the root logger
		LoggerConfig rootLoggerConfiguration = loggerContext.getConfiguration().getLoggerConfig(root.getName());
		// obtain the list of appenders attached to the root logger
		Map<String, Appender> appenders = rootLoggerConfiguration.getAppenders();

		// loop through all root logger appenders
		for (String appenderName : appenders.keySet()) {
			// get current appender
			Appender appender = appenders.get(appenderName);
			// root.error("APPENDER FOUND: " + appenderName + "/" + appender.getClass().getName());

			// look for the file appender
			if (appender instanceof MetaAppenderLog4J2) {
				MetaAppenderLog4J2.metaAppender = (MetaAppenderLog4J2) appender;
				return;
			} else if (appender instanceof RollingFileAppender) {

				// file appender has been found - this is the one for the mirth.log
				RollingFileAppender mainLogAppender = (RollingFileAppender) appender;

				/** first read it's properties as they will serve as default values for the channel appenders */

				// there should be only one so take the lazy way
				SizeBasedTriggeringPolicy triggeringPolicy = (SizeBasedTriggeringPolicy) ((CompositeTriggeringPolicy) mainLogAppender
						.getTriggeringPolicy()).getTriggeringPolicies()[0];

				DefaultRolloverStrategy rolloverStrategy = (DefaultRolloverStrategy) mainLogAppender.getManager().getRolloverStrategy();

				// determine the max size of log files. If none was specified, use the initial config as default
				this.configMaxFileSize = (customMaxFileSize != null) ? SizeBasedTriggeringPolicy.createPolicy(customMaxFileSize).getMaxFileSize()
						: triggeringPolicy.getMaxFileSize();

				// determine the max number of log files per channgel - default value is the same size like mirth.log
				this.configMaxBackupIndex = (customMaxBackupIndex != null) ? customMaxBackupIndex : rolloverStrategy.getMaxIndex();
				// determine the layout pattern of the log entries - default layout is the same like mirth.log
				this.configLayout = (customLogPattern != null) ? customLogPattern : mainLogAppender.getLayout().toString();

				try {
					// if a custom log file location was set
					if((customLogPath != null) && (customLogPath.length() > 0)) {
						try {
							// assure that the path actually exists
							Files.createDirectories(Paths.get(customLogPath));
							
						} catch (IOException e) {
							// if path could not be created, use the one configured by the mirth log4j2 configuration
							customLogPath = null;
						}
					}

					// set the log file location
					this.configLogLocation = (customLogPath == null) ? Paths.get(mainLogAppender.getFileName()).getParent().toFile().getAbsolutePath() : customLogPath;
					
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					ExceptionUtils.printRootCauseStackTrace(e, new PrintWriter(sw));
					String exceptionAsString = sw.toString();
					root.error("CLASS = " + appender.getClass().getCanonicalName() + "   Meta: "
							+ (appender instanceof lu.hrs.mirth.MetaAppenderLog4J2));
					root.error("INSTANTIATION EXCEPTION5 (" + MetaAppenderLog4J2.metaAppender + "): " + exceptionAsString);
				}

				// default value is "false"
				this.logAllToMainLog = (logAllToMainLog != null) && logAllToMainLog;

				// Create a new appender with the new name and the same configuration
				Appender fileAppender = createRollingFileAppender(mainLogAppenderName, this.configLogLocation, this.configMaxFileSize,
						this.configMaxBackupIndex, this.configLayout.toString());

				// and add the new one to the appender list
				addAppender(fileAppender);

				// remove the current file appender from the root logger
				mainLogAppender.stop();
				
				rootLoggerConfiguration.removeAppender(appenderName);
			} else if (appender instanceof ConsoleAppender) {
				// console appender has been found
				ConsoleAppender console = (ConsoleAppender) appender;
				// remove it from the root logger
				rootLoggerConfiguration.removeAppender(appenderName);
				console.stop();

				// Create a new appender with the new name and the same configuration
				org.apache.logging.log4j.core.appender.ConsoleAppender.Builder newAppenderBuilder = ConsoleAppender.newBuilder();
				// set it's name
				newAppenderBuilder.setName(consoleAppenderName);

				// and add it instead to the appender list
				addAppender(newAppenderBuilder.build());
			} else {
				rootLoggerConfiguration.removeAppender(appenderName);
				ArrayAppender dashboard = (ArrayAppender) appender;
				// ToDo: Change appender name - only if really bored as this is a pain in the neck w/ log4j2...
				mirthArrayAppenderName = dashboard.getName();
				// and add it to the appender list
				addAppender(appender);

				// and withdraw the root logger the control about this appender
				rootLoggerConfiguration.removeAppender(mirthArrayAppenderName);
			}
		}

		// there shall only be one - me!
		loggerContext.getConfiguration().addLoggerAppender(root, this);
		this.start();
		// not yet sure if really needed
		loggerContext.updateLoggers();

		// remember the reference to this object for subsequent calls
		MetaAppenderLog4J2.metaAppender = this;
	}

	/**
	 * Creates a builder for a new rolling file appender
	 * 
	 * @param appenderName
	 *            The name of the new appender (usually the channel name)
	 * @param filePath
	 *            The location where the logfiles created by this appender will be placed
	 * @param maxFileSize
	 *            The maximum size of a log file until a new logfile will be created
	 * @param maxNumberOfFiles
	 *            The maximum number of log files that will be created for this appender before the oldest one will be overwritten
	 * @param logPattern
	 *            The layout of a log entry
	 * @return A new rolling file appender
	 */
	private Appender createRollingFileAppender(String appenderName, String filePath, Long maxFileSize, Integer maxNumberOfFiles, String logPattern) {

		// create a builder instance
		ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
		// create the fully qualified path of the log file
		String fileName = filePath + File.separator + appenderName + ".log";

		// create a new appender
		AppenderComponentBuilder appenderBuilder = builder.newAppender(appenderName, "RollingFile").addAttribute("fileName", fileName)
				.addAttribute("filePattern", fileName + ".%i");

		// Define maximum size of a log file
		appenderBuilder.addComponent(
				builder.newComponent("Policies").addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", maxFileSize)));

		// define maximum number of log files
		appenderBuilder.addComponent(builder.newComponent("DefaultRolloverStrategy").addAttribute("max", maxNumberOfFiles));

		// define log entry layout
		appenderBuilder.add(builder.newLayout("PatternLayout").addAttribute("pattern", logPattern));

		// add the component to the configuration
		builder.add(appenderBuilder);

		// and get the appender
		return builder.build().getAppenders().get(appenderName);
	}

	/**
	 * Adds an appender to the cache
	 * 
	 * @param appender
	 *            The appender that should be added to the cache
	 */
	private void addAppender(Appender appender) {
		synchronized (appenders) {
			appenders.put(appender.getName(), appender);
			// // get the logger context managing the list of loggers, appenders and their configuration
			// LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
			//
			// // get root logger
			// Logger root = loggerContext.getRootLogger();
			// root.error("Number of Appenders: " + appenders.values().size());
		}
	}

	/**
	 * Provides the appender for the log file corresponding to the channel name
	 * 
	 * @param channelName
	 *            The name of the channel for which the appender should be provided. If the name is not found in the cache, a new appender is created.
	 *            If the name is null, the appender for the general mirth log is provided.
	 * @return The appender corresponding the provided channel name
	 */
	public Appender getAppender(String channelName) {

		synchronized (appenders) {

			// no channel name
			if (channelName == null) {
				// means main appender
				channelName = mainLogAppenderName;

				// if there is not yet an appender for the channel
			} else if (!appenders.containsKey(channelName)) {
				// create a channel-centric appender
				Appender channelAppender = createRollingFileAppender(channelName, this.configLogLocation, this.configMaxFileSize,
						this.configMaxBackupIndex, this.configLayout);
				// activate it
				channelAppender.start();
				// and place the appender in the cache
				addAppender(channelAppender);
			}

		}
		return appenders.get(channelName);
	}

	/**
	 * Close all appenders
	 */
	public void close() {
		synchronized (appenders) {
			for (Appender appender : appenders.values()) {
				appender.stop();
			}
		}

		MetaAppenderLog4J2.metaAppender = null;
	}

	@Override
	public void append(LogEvent event) {

		Appender appender = null;

		/** Check if the log message is only determined for a specific log location */

		// get the log message
		Message message = event.getMessage();
		String content = message.getFormattedMessage();

		boolean fileOnly, dashboardOnly, consoleOnly;
		fileOnly = dashboardOnly = consoleOnly = false;

		// check log message for special instructions
		if (content.length() > 3) {
			switch (content.substring(0, 4).toUpperCase()) {
			case "#FO:":
				// log only to log file
				fileOnly = true;
				break;
			case "#DO:":
				// log only to the mirth administrator dashboard
				dashboardOnly = true;
				break;
			case "#CO:":
				// log only to the console
				consoleOnly = true;
				break;
			default:
				break;
			}

			if (fileOnly || dashboardOnly || consoleOnly) {
				// flag was understood & set - remove the instruction from the log message
				message = new SimpleMessage(content.substring(4));
			}
		}

		/** Try to determine channel name */

		String channelName = null;
		// get logger name
		String loggerName = event.getLoggerName();

		// try to identify the channel from which the appender was called
		Matcher uuidMatcher = patternUuid.matcher(event.getThreadName());

		// check if the thread name contains a channel reference
		if (!uuidMatcher.find()) {
			// There is no trace in the thread name. So try to determine the channel via the logger name.
			uuidMatcher = patternUuid.matcher(loggerName);
		}

		// if there is a trace of the channel causing the log entry
		if (uuidMatcher.find(0)) {
			// use it to determine the channel name
			channelName = ChannelUtil.getChannelName(uuidMatcher.group());
			// and also adapt the logger name to reflect the channel name as well
			loggerName = channelName + "-" + loggerName.replaceFirst(uuidMatcher.group() + "-?", "");
		}

		/** Log to the channel-specific log file */

		event = new Log4jLogEvent(loggerName, event.getMarker(), event.getLoggerFqcn(), event.getLevel(), message, null, event.getThrown());

		// if a channel was identified
		if ((channelName != null) && !dashboardOnly && !consoleOnly) {
			// call the right appender dependent on the channel
			appender = getAppender(channelName);

			// write the message to the appender.
			appender.append(event);
		}

		// if the log message is not channel-specific or if user configured to log all messages also to the main log file
		if ((logAllToMainLog || (channelName == null)) && (!dashboardOnly && !consoleOnly)) {
			// log event also to the main log file
			getAppender(mainLogAppenderName).append(event);
		}

		// all events that are logged as error
		if (event.getLevel() == Level.ERROR) {
			// are also accumulated in a specific log
			getAppender(errorAppenderName).append(event);
		}

		// if a special mode has been activated to focus on a channel in the dashboard and/or to omit logging of a channel from the dashboard
		// add a special prefix to all dashboard log messages to indicate this situation
		if ((this.focusedChannelName != null) || (this.filteredChannelName != null)) {
			String loggerPrefix = "";

			// if the both, focusing and filtering, are activated, indicate it to the user
			if ((this.focusedChannelName != null) && (this.filteredChannelName != null)) {
				// set the logger prefix to focused and filtered
				loggerPrefix = "FOCUSED & FILTERED: ";
			} else if ((this.focusedChannelName != null) && appenders.containsKey(channelName)) {
				// if a channel is focused and actually deployed (or was at least deployed once)

				// but the current event was caused by a different channel
				if (!this.focusedChannelName.equals(channelName)) {
					// omit logging to dashboard and console for this event
					return;
				}
				// set the logger prefix to focused
				loggerPrefix = "FOCUSED: ";
			} else if ((this.filteredChannelName != null) && appenders.containsKey(channelName)) {
				// if a channel is filtered and actually deployed (or was at least deployed once)

				// and the current event was caused by the filtered channel
				if (this.filteredChannelName.equals(channelName)) {
					// omit logging to dashboard and console for this event
					return;
				}
				// set the logger prefix to filtered
				loggerPrefix = "FILTERED: ";
			}

			// adapt the logging event in order to include channel name in component description
			event = new Log4jLogEvent(loggerPrefix + event.getLoggerName(), event.getMarker(), event.getLoggerFqcn(), event.getLevel(),
					event.getMessage(), null, event.getThrown());
		}

		if (!fileOnly && !dashboardOnly) {
			// and write the event to the console
			getAppender(consoleAppenderName).append(event);
		}
		if (!fileOnly && !consoleOnly) {
			// and also to the dashboard
			getAppender(mirthArrayAppenderName).append(event);
		}
	}
}