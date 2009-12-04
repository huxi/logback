/**
 * Logback: the generic, reliable, fast and flexible logging framework.
 *
 * Copyright (C) 2000-2008, QOS.ch
 *
 * This library is free software, you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation.
 */
package ch.qos.logback.classic.twitter;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Collections;


/**
 * This appender tweets the message on the public Twitter timeline of the user
 * specified setting Username and Password.
 *
 * You can set a list of users that should receive the message as a reply using ReplyUsers.
 *
 * You can also set a list of users that should receive the message as a direct message using DirectMessageUsers.
 * Remember that they must follow you to be able to do so.
 *
 * Lists of users are , delimited.
 *
 * @author Joern Huxhorn
 */
public class TwitterAppender
	extends UnsynchronizedAppenderBase<LoggingEvent>
{
	private String username;
	private String password;
	
	private List<String> replyUserList =new ArrayList<String>();
	private List<String> directMessageUserList =new ArrayList<String>();
	private Twitter twitter;
	private static final String MSG = "About to tweet another event...";


	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getPassword()
	{
		return password;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public void addReplyUser(String user)
	{
		user=prepareUser(user);
		if(user != null)
		{
			if(!replyUserList.contains(user))
			{
				replyUserList.add(user);
			}
		}
	}

	public void setReplyUsers(String replyUsers)
	{
		replyUserList=createUserList(replyUsers);
	}

	public void addDirectMessageUser(String user)
	{
		user=prepareUser(user);
		if(user != null)
		{
			if(!directMessageUserList.contains(user))
			{
				directMessageUserList.add(user);
			}
		}
	}

	public void setDirectMessageUsers(String directMessageUsers)
	{
		directMessageUserList=createUserList(directMessageUsers);
	}

	/**
	 * Parses comma-separated list of twitter users.
	 * Removes @ if available.
	 * Returns an empty list if users is null.
	 *
	 * @param users a string containing comma separated users.
	 * @return the parsed users or an empty list if users is null.
	 */
	private static List<String> createUserList(String users)
	{
		ArrayList<String> result = new ArrayList<String>();
		if(users != null)
		{
			StringTokenizer tok=new StringTokenizer(users, ",", false);
			while(tok.hasMoreTokens())
			{
				String current = tok.nextToken();
				current=prepareUser(current);

				// ignore malformed names & duplicates
				if(current != null && !result.contains(current))
				{
					result.add(current);
				}
			}
		}
		return result;
	}

	private static String prepareUser(String user)
	{
		user=user.trim();
		if(user.startsWith("@"))
		{
			// remove a superfluous @
			user = user.substring(1);
		}
		if("".equals(user))
		{
			// we ignore these later.
			user=null;
		}
		return user;
	}

	public List<String> getReplyUserList()
	{
		return Collections.unmodifiableList(replyUserList);
	}

	public List<String> getDirectMessageUserList()
	{
		return Collections.unmodifiableList(directMessageUserList);
	}

	public void start()
	{
		twitter=new Twitter(username, password);
		super.start();
	}

	public void stop()
	{
		super.stop();
		twitter=null;
	}

	protected void updateStatus(Twitter twitter, String user, String message, boolean direct)
			throws TwitterException
	{
		if(user == null)
		{
			if(message.length()> 140)
			{
				message=message.substring(0,140);
			}
			twitter.updateStatus(message);
			return;
		}
		if(direct)
		{
			// direct messages can have 140 characters excluding username
			if(message.length()> 140)
			{
				message=message.substring(0,140);
			}
			twitter.updateStatus("d "+user+" "+message);
		}
		else
		{
			message = "@"+user+" "+message;
			// replies can have 140 characters including username
			if(message.length()> 140)
			{
				message=message.substring(0,140);
			}
			twitter.updateStatus(message);
		}
	}

	protected void append(LoggingEvent event)
	{
		Twitter localTwitter=twitter;
		if(localTwitter != null)
		{
			try
			{
				// this is necessary to see repeated updates with the exact same text.
				localTwitter.updateStatus(MSG);

				String output = this.layout.doLayout(event);

				// first of all, update the global timeline with the message...
				updateStatus(localTwitter, null, output, false);

				{ // send @reply to every user
					List<String> localList = replyUserList;
					for(String current:localList)
					{
						updateStatus(localTwitter, current, output, false);
					}
				}

				{ // send DM to every user
					List<String> localList = directMessageUserList;
					for(String current:localList)
					{
						updateStatus(localTwitter, current, output, true);
					}
				}
			}
			catch (TwitterException e)
			{
				e.printStackTrace();
				// What shall we do here? This can happen anytime and shouldn't be a problem.
			}
		}
	}

	public String toString()
	{
		return "TwitterAppender[username="+username+", replyUserList="+replyUserList+", directMessageUserList="+directMessageUserList+"]";
	}
}
