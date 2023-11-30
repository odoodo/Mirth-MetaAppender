**Context**<br/>
* This repository enhances the Mirth enterprise service bus, an application integration middleware that enables the information exchange between arbitrary applications in the healthcare sector.
* The source code of the open source version of this dual-licenced product can be found in [this repository](https://github.com/nextgenhealthcare/connect).
* The quite active community around Mirth mainly exchanges via [this forum](https://forums.mirthproject.io/forum/mirth-connect) and [that]([https://forums.mirthproject.io/forum/mirth-connect](https://github.com/nextgenhealthcare/connect/discussions)) forum.
* The company providing a ready to go download version as well as commercial support for this product is [NextGen Healthcare](https://www.nextgen.com/products-and-services/nextgen-connect-integration-engine-downloads)

**Functionality**<br/>
This library eliminates Mirth logging limitations by replacing the logging mechanism.

![Image description](https://github.com/odoodo/Mirth-MetaAppender/blob/master/MirthExtendedLoggingScheme.PNG)

* All log messages created by a channel will be placed in a separate log file named like the channel itself.
* Messages that are not caused by a channel (e.g. global deploy script) are logged to the standard log file [I]mirth.log[/I].
* All messages on log level ERROR are accumulated in a specific log file called [I]mirthErrors.log[/I].
* All messages that are logged to the console or administrator dashboard are enriched with the name of the channel causing the message.
* This library is compatible with the Log4J implementation (till v4.0.1) and the Log4j2 implementation (from v4.1.0) of Mirth Connect

**How to use:**


1. Create a jar file from the code or download [the latest release](https://github.com/odoodo/Mirth-MetaAppender/releases)
1. Copy the jar to the ***custom-lib*** subfolder of your mirth installation
1. Add the following code to the global deploy script:<br/>
**Packages.lu.hrs.mirth.MetaAppender.activate();**<br/>(just needs to be called once after the service is started but multiple calls do not do harm)
1. :exclamation:**If you are using the function [activateChannelLogging()](https://forums.mirthproject.io/forum/mirth-connect/support/16039-automatic-channel-centric-logging), remove it or remove it's content if already referenced in many places**:exclamation:
1. Restart the mirth service


**Customization:**<br/>
By default the logging configuration of *log4j.properties* respectively *log4j2.properties* in the subfolder *.\config* of the mirth installation is used. It is however possible to overwrite certain parameters by providing them to the **activate()** call:<br/>

`Packages.lu.hrs.mirth.MetaAppender.activate(<customLogPath>, <customMaxFileSize>, <customMaxBackupIndex>, <customLogPattern>, <logAllToMainLog>);`<br/> 

**customLogPath** - Defines a custom location for the log files<br/> 
**customMaxFileSize** - Defines a custom maximal size per log file<br/>
**customMaxBackupIndex** - Defines a maximum number of log files that will be created per channel till the oldest is overwritten (round-robin)<br/>
**customLogPattern** - Defines a custom structure for the log file entries<br/>
**logAllToMainLog** - If this flag is set, all log entries (also channel-specific ones) will also be logged to the main log. (off by default)<br/>
* All parameter are optional and can be expressed by null. 
* Tailing parameters can be omitted.

**Examples:**<br/>
Writes the channel-specific log entries in a custom structure:<br/>
`Packages.lu.hrs.mirth.MetaAppender.activate(null, null, null, "%d %-5p %c: %m%n");`<br/>

Extends the size of all log files to 5MB and logs all channel data also to the main log file (mirth.log):<br/>
 `Packages.lu.hrs.mirth.MetaAppender.activate(null, '5MB', null, null, true);`<br/>

**Further features:**<br/>
***Focus on specific channel log***<br/>
If many channels are logging to the dashboard, you might want to focus on the log output of one specific channel if e.g. an issue occurs.<br/>
This can be done via:<br/>
`Packages.lu.hrs.mirth.MetaAppender.setFocus(<Channel name or id>);`<br/>
Focus can be removed by:<br/>
`Packages.lu.hrs.mirth.MetaAppender.removeFocus();`<br/>
* Setting a focus does not influence the logging to the channel log-files but only the dashboard.
* If a focus is set, this is indicated by a "**FOCUSED:** "-prefix before the channel name of each log entry.

***Suppress specific channel log***<br/>
In case of an issue with a specific channel, you might want to log additional information for tracking down the bug (even in prod env :sunglasses:). 
However, while having additional information in the channel log, you probably do not want to have your dashboard log spammed.<br/>
This can be done via:<br/>
`Packages.lu.hrs.mirth.MetaAppender.setFilter(<Channel name or id>);`<br/>
Focus can be removed by:<br/>
`Packages.lu.hrs.mirth.MetaAppender.removeFilter();`<br/>
* Setting a filter does not influence the logging to the channel log-files but only the dashboard.
* If a filter is set, this is indicated by a "**FILTERED:** "-prefix before the channel name of each log entry.

***Log a message to a specific location***<br/>
A scope-prefix can be used in a log message in order to only log it to the dashboard, logfile, or console.

The following scopes do exist:
| Prefix | Meaning | Description |
|---|---|---|
|**#DO:**|dashboard only|log message is only written to the dashboard.|
|**#FO:​**|file only|log message is only written to the file. (channel log file and/or mirth.log - depending on the configuration)|
|**#CO:​**|console only|log message is only written to the console.|

*Examples:*

*Standard log message:*
```js
 logger.info('This message will appear on dashboard, log file & console');
```
*Log to **dashboard only** by using the **#DO:** - prefix:*
```js
logger.info('#DO: This message will only appear on dashboard');
```
*Log to **file only** by using the **#FO:** - prefix:​*
```js
​logger.info('#FO: This message will only appear in the log file');
```
*Log to **console only** by using the **#CO:** - prefix:​*
```js
logger.info('#CO: This message will only appear on the console');
```
*The prefix itself will not appear in the displayed log message.*
