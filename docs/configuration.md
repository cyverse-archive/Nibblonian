Configuring Nibblonian
----------------------

Nibblonian pulls its configuration from Zookeeper. The properties must be loaded into Zookeeper with Clavin.

There is also a log4j config file located at /etc/nibblonian/log4j.properties. It's a normal log4j properties file that looks like this by default.

    # Jargon-related configuration
    log4j.rootLogger=WARN, A, B

    log4j.appender.B=org.apache.log4j.ConsoleAppender
    log4j.appender.B.layout=org.apache.log4j.PatternLayout
    log4j.appender.B.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n

    log4j.appender.A=org.apache.log4j.FileAppender
    log4j.appender.A.File=nibblonian.log
    log4j.appender.A.layout=org.apache.log4j.PatternLayout
    log4j.appender.A.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n

The iRODS configuration should look fairly similar to other systems that interact with iRODS. defaultResource is mentioned on the off-chance that we need it, but it's fine to leave it blank.

The log4j configuration section is just a bog-standard log4j configuration. It configures two loggers by default, one that goes to stdout and another that goes to a log file. You might want to disable the ConsoleAppender, but leaving it in shouldn't hurt anything.