package ch.qos.logback.classic.twitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TwitterAppenderExample
{

	public static void main(String[] args)
	{
		final Logger logger = LoggerFactory.getLogger(TwitterAppenderExample.class);

		System.out.println("Remember to set username and password in logback-text.xml");
		logger.info("Does it work? {}", "foo");
	}
}
