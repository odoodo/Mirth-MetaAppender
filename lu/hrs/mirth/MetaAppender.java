package lu.hrs.mirth;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.spi.LoggingEvent;

import com.mirth.connect.server.userutil.ChannelUtil;


/**
 * This custom appender hijacks the Mirth logging mechanism and reroutes channel-specific logging to a log file named like the channel causing the log
 * event. None channel-specific log messages are still routed to the main log file. Further, the channel name is added to all channel specific log 
 * entries for the main log and all logs to the console or dashboard.
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, 
 * You can obtain one at https://mozilla.org/MPL/2.0/.
 * 
 * @author ortwin.donak
 * 
 */
public class MetaAppender extends RollingFileAppender {

	private static final Pattern patternUuid = Pattern.compile("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})");
	private static final String mainLogAppenderName = "mirth";
	private static final String errorAppenderName = "mirthErrors";
	private static final String consoleAppenderName = "console";
	private static final String mirthArrayAppenderName = "mirthDashboard";
	private static MetaAppender metaAppender = null;
	private final HashMap<String, Appender> appenders = new HashMap<String, Appender>();
	private Long configMaxFileSize = null;
	private Integer configMaxBackupIndex = null;
	private Layout configLayout = null;
	private String configLogLocation = null;
	private Priority configThreshold = null;
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
		if((identifier == null) || identifier.isEmpty()){
			return null; 
		}
		
		// normalize
		identifier = identifier.trim();
		// get the current instance of the appender or create it if not yet existing
		MetaAppender appender = getInstance(null, null, null, null, null);
		// resolve the channel id to the channel name, if the provided identifier is a channel name
		String channelName = ChannelUtil.getChannelName(identifier);
		// determine the name of the channel that should be focused and set it as focused channel.
		appender.focusedChannelName = (channelName != null) ? channelName : identifier;
		
		// assure that a channel is not focused and filtered at the same time
		if(appender.focusedChannelName.equals(appender.filteredChannelName)){
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
		MetaAppender appender = getInstance(null, null, null, null, null);
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
		if((identifier == null) || identifier.isEmpty()){
			return null; 
		}
		
		// normalize
		identifier = identifier.trim();
		// get the current instance of the appender or create it if not yet existing
		MetaAppender appender = getInstance(null, null, null, null, null);
		// resolve the channel id to the channel name, if the provided identifier is a channel name
		String channelName = ChannelUtil.getChannelName(identifier);
		// determine the name of the channel that should be filtered and set it as filtered channel.
		appender.filteredChannelName = (channelName != null) ? channelName : identifier;
		
		// assure that a channel is not focused and filtered at the same time
		if(appender.filteredChannelName.equals(appender.focusedChannelName)){
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
		MetaAppender appender = getInstance(null, null, null, null, null);
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
	public static MetaAppender activate() {
		return getInstance(null, null, null, null, null);
	}

	/**
	 * Activates the customization of the mirth logging mechanism (Just has to be called once)
	 * 
	 * @param customLogPath
	 *            Defines a custom location for the log files (OPTIONAL)
	 * @return A reference to the Meta file appender. Usually this is not needed as everything is handled automatically.
	 */
	public static MetaAppender activate(String customLogPath) {
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
	public static MetaAppender activate(String customLogPath, String customMaxFileSize) {
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
	public static MetaAppender activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex) {
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
	public static MetaAppender activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern) {
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
	public static MetaAppender activate(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern, Boolean logAllToMainLog) {
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
	private static MetaAppender getInstance(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern,
			Boolean logAllToMainLog) {

			return (MetaAppender.metaAppender != null) ? MetaAppender.metaAppender
					: new MetaAppender(customLogPath, customMaxFileSize, customMaxBackupIndex, customLogPattern, logAllToMainLog);
	}

	private MetaAppender() {
		// omit having called the constructor directly
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
	private MetaAppender(String customLogPath, String customMaxFileSize, Integer customMaxBackupIndex, String customLogPattern,
			Boolean logAllToMainLog) {
		Logger root = null;
		com.mirth.connect.plugins.serverlog.ServerLogItem.DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
		// get root logger
		 root = Logger.getRootLogger();

		// loop through all appenders attached to the root logger
		for (Appender appender : Collections.list((Enumeration<Appender>) root.getAllAppenders())) {
			// look for the file appender
			if (appender instanceof MetaAppender) {
				MetaAppender.metaAppender = (MetaAppender) appender;
				return;
			}else if (appender instanceof RollingFileAppender) {
				// file appender has been found
				RollingFileAppender mainLogAppender = (RollingFileAppender) appender;
				// if no custom properties have been provided, use the configuration of log4j.properties as default
				if (customMaxFileSize != null) {
					// transform the custom max file size to a number
					this.setMaxFileSize(customMaxFileSize);
					// and fetch it
					this.configMaxFileSize = this.getMaximumFileSize();
				} else {
					// no custom max file size has been provided - use the one from properties as default
					this.configMaxFileSize = mainLogAppender.getMaximumFileSize();
				}
				// default value is the same size like mirth.log
				this.configMaxBackupIndex = (customMaxBackupIndex != null) ? customMaxBackupIndex : mainLogAppender.getMaxBackupIndex();
				// default layout is the same like mirth.log
				this.configLayout = (customLogPattern != null) ? new PatternLayout(customLogPattern) : mainLogAppender.getLayout();
				// log level will be the same like mirth.log
				this.configThreshold = mainLogAppender.getThreshold();
				try{
				// files will be placed at the same location like mirth.log
				this.configLogLocation = Paths.get(mainLogAppender.getFile()).getParent().toString();
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					ExceptionUtils.printRootCauseStackTrace(e,new PrintWriter(sw));
					String exceptionAsString = sw.toString();
					 root = Logger.getRootLogger();
					root.setLevel(Level.INFO);
					root.error("CLASS = "+appender.getClass().getCanonicalName()+"   Meta?: "+ (appender instanceof lu.hrs.mirth.MetaAppender));
					root.error("INSTANTIATION EXCEPTION5 ("+MetaAppender.metaAppender+"): "+exceptionAsString);
					

				}
				// default value is "false"
				this.logAllToMainLog = (logAllToMainLog != null) && logAllToMainLog;
				// remove the current file appender from the root logger
				root.removeAppender(mainLogAppender);
				// change its name
				mainLogAppender.setName(mainLogAppenderName);
				// and add it instead to the appender list
				addAppender(mainLogAppender);
			} else if (appender instanceof ConsoleAppender) {
				// console appender has been found
				ConsoleAppender console = (ConsoleAppender) appender;
				// remove it from the root logger
				root.removeAppender(console);
				// change its name
				console.setName(consoleAppenderName);
				// and add it instead to the appender list
				addAppender(console);
			} else {
				// mirth uses a custom appender plugin (ArrayAppender) for displaying messages at its dashboard.
				// Unfortunately w/o a name - adjust that
				appender.setName(mirthArrayAppenderName);
				// and add it to the appender list
				addAppender(appender);
				// and withdraw the root logger the control about this appender
				root.removeAppender(appender);
			}
		}

		// finally attach the meta appender to the root logger
		root.addAppender(this);
		
		// remember the reference to this object for subsequent calls
		MetaAppender.metaAppender = this;
		 root = Logger.getRootLogger();
			root.setLevel(Level.INFO);
		root.error("META APPENDER = "+MetaAppender.metaAppender);
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
				// create a new appender instance
				RollingFileAppender appender = new RollingFileAppender();
				// define the maximum size of one log file
				appender.setMaximumFileSize(this.configMaxFileSize);
				// define the maximum number of log files
				appender.setMaxBackupIndex(this.configMaxBackupIndex);
				// set the format of the log string
				appender.setLayout(this.configLayout);
				// define the log file path
				appender.setFile(String.format("%s%s%s.log", configLogLocation, File.separator, channelName));
				// set the appender name
				appender.setName(channelName);
				// set logging threshold of main logger
				appender.setThreshold(this.configThreshold);
				// contribute to pre-existing log
				appender.setAppend(true);
				// now apply everything
				appender.activateOptions();
				// and place the appender in the cache
				addAppender(appender);
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
				appender.close();
			}
		}
		
		MetaAppender.metaAppender = null;
	}

	@Override
	public void append(LoggingEvent event) {
		Appender appender = null;
		// try to identify the channel from which the appender was called
		Matcher uuidMatcher = patternUuid.matcher(event.getThreadName());
		// try to determine the channel name
		String channelName = uuidMatcher.find() ? ChannelUtil.getChannelName(uuidMatcher.group()) : null;

		// call the right appender dependent on the channel (or the main appender if log entry was not caused by a channel)
		appender = getAppender(channelName);
		// and write the entry to the log file
	//	appender.doAppend(event);

		// in case of channel logging
		if (channelName != null) {
			// adapt the logging event in order to include channel name in component description
			event = new LoggingEvent(event.getFQNOfLoggerClass(), Logger.getLogger(channelName + "-" + event.getLoggerName()), event.getTimeStamp(),
					event.getLevel(), event.getMessage(), event.getThreadName(), event.getThrowableInformation(), event.getNDC(),
					event.getLocationInformation(), event.getProperties());
		}

		// if user configured to log all messages also to the main log file and the current logging is done to a channel-specific file
		if (logAllToMainLog && !MetaAppender.mainLogAppenderName.equals(appender.getName())) {
			// log event also to the main log file
			getAppender(mainLogAppenderName).doAppend(event);
		}
		
		// all events that are logged as error
		if (event.getLevel() == Level.ERROR) {
			// are also accumulated in a specific log
			getAppender(errorAppenderName).doAppend(event);
		}
		
		if ((this.focusedChannelName != null) || (this.filteredChannelName != null)) {
			String loggerPrefix = null;
			
			// if a channel is focused and actually deployed (or was at least deployed once)
			if ((this.focusedChannelName != null) && appenders.containsKey(channelName)) {
				// but the current event was caused by a different channel
				if (!this.focusedChannelName.equals(channelName)) {
					// omit logging to dashboard and console for this event
					return;
				}
				// set the logger prefix to focused
				loggerPrefix = "FOCUSED: ";
			}
			
			// if a channel is filtered and actually deployed (or was at least deployed once)
			if ((this.filteredChannelName != null) && appenders.containsKey(channelName)) {
				// and the current event was caused by the filtered channel
				if (this.filteredChannelName.equals(channelName)) {
					// omit logging to dashboard and console for this event
					return;
				}
				// set the logger prefix to filtered
				loggerPrefix = "FILTERED: ";
			}
			
			// if the both, focusing and filtering, are activated, indicate it to the user
			if ((this.focusedChannelName != null) && (this.filteredChannelName != null)){
				// set the logger prefix to focused and filtered
				loggerPrefix = "FOCUSED & FILTERED: ";				
			}
			
			// adapt the logging event in order to include channel name in component description
			event = new LoggingEvent(event.getFQNOfLoggerClass(), Logger.getLogger(loggerPrefix + event.getLoggerName()), event.getTimeStamp(),
					event.getLevel(), event.getMessage(), event.getThreadName(), event.getThrowableInformation(), event.getNDC(),
					event.getLocationInformation(), event.getProperties());
		}
		
		// and write the event to the console
		getAppender(consoleAppenderName).doAppend(event);
		// and also to the dashboard
		getAppender(mirthArrayAppenderName).doAppend(event);
	}

}