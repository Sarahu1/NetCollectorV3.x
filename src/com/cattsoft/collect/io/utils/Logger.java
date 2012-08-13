/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/*
 Copyright (c) 2006-2012 ymnk, JCraft,Inc. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
 this list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright 
 notice, this list of conditions and the following disclaimer in 
 the documentation and/or other materials provided with the distribution.

 3. The names of the authors may not be used to endorse or promote products
 derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
 INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.cattsoft.collect.io.utils;

import org.slf4j.LoggerFactory;

/**
 * 日志工具
 * 
 * @author ChenXiaohong
 * 
 */
public class Logger {
	public static final int DEBUG = 0;
	public static final int INFO = 1;
	public static final int WARN = 2;
	public static final int ERROR = 3;
	public static final int FATAL = 4;

	public boolean isEnabled(int level) {
		return true;
	}

	/** 打印日志.
	 * @param level 日志等级
	 * @param message 信息
	 */
	public void log(int level, String message) {
		//
	}
	
	/** 打印日志.
	 * @param level 日志等级
	 * @param message 信息
	 * @param e
	 */
	public void log(int level, String message, Throwable e) {
		// 
	}

	/**
	 * @return the logger
	 */
	public static Logger getLogger(final Class<?> cls) {
		// 检测
		boolean checked = true;
		try {
			// org.slf4j.Logger
			ClassLoader.getSystemClassLoader().loadClass("org.slf4j.Logger");
		} catch (Exception e) {
			checked = false;
		}
		try {
			// org.apache.log4j.Logger
			ClassLoader.getSystemClassLoader().loadClass("org.apache.log4j.Logger");
		} catch (Exception e) {
			checked = false;
		}
		try {
			// java.util.logging.Logger
			ClassLoader.getSystemClassLoader().loadClass(
					"java.util.logging.Logger");
		} catch (Exception e) {
			checked = false;
		}
		return checked ? new DefaultLogger(cls) : SIMPLE_LOGGER;
	}

	/**
	 * 简单日志, 输出到控制台
	 */
	public static final Logger SIMPLE_LOGGER = new Logger() {
		public boolean isEnabled(int level) {
			return true;
		}

		public void log(int level, String message) {
			if (level == ERROR)
				System.out.println(message);
			else
				System.out.println(message);
		}
	};
	/*
	 * final Logger DEVNULL=new Logger(){ public boolean isEnabled(int
	 * level){return false;} public void log(int level, String message){} };
	 */
}

/** 默认日志输出工具
 * @author ChenXiaohong
 *
 */
class DefaultLogger extends Logger {
	org.slf4j.Logger sl4j_logger = null;
	java.util.logging.Logger lang_logger = null;
	org.apache.log4j.Logger log4j_logger = null;
	
	/**
	 * @param cls
	 */
	public DefaultLogger(Class<?> cls) {
		try {
			sl4j_logger = LoggerFactory.getLogger(cls);
		} catch (Error e) {
			try {
				log4j_logger = org.apache.log4j.Logger.getLogger(cls);
			} catch (Error e1) {
				try {
					lang_logger = java.util.logging.Logger.getLogger(cls
							.getName());
				} catch (Error e2) {
					// 无法记录日志
				}
			}
		}
	}
	
	public void log(int level, String message) {
		log(level, message, null);
	}

	public void log(int level, String message, Throwable e) {
		switch (level) {
		case DEBUG:
			if (null != sl4j_logger)
				sl4j_logger.debug(message);
			else if(null != log4j_logger)
				log4j_logger.debug(message);
			else if (null != lang_logger)
				lang_logger.info(message);
			else
				System.out.println(message);
			break;
		case INFO:
			if (null != sl4j_logger)
				sl4j_logger.info(message);
			else if(null != log4j_logger)
				log4j_logger.info(message);
			else if (null != lang_logger)
				lang_logger.info(message);
			else
				System.out.println(message);
			break;
		case ERROR:
			if (null != sl4j_logger)
				sl4j_logger.error(message, e);
			else if(null != log4j_logger)
				log4j_logger.error(message, e);
			else if (null != lang_logger)
				lang_logger.severe(message);
			else
				System.err.println(message);
			break;
		default:
			System.out.println(message);
			break;
		}
	}
}
