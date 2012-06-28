/**
 * 
 */
package com.cattsoft.collect.net.adapter;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.net.listener.BaseListener;
import com.cattsoft.collect.net.process.ProcessResult;

/**
 * 基础适配器
 * @author ChenXiaohong
 */
public abstract class BaseAdaptor extends BaseListener {
	private Logger logger = LoggerFactory.getLogger(getClass());
	/*** 适配表达式字典*/
	protected Map<String, String> dictionary = new HashMap<String, String>();
	/*** 适配表达式*/
	protected final String expression;
	/*** 适配正则*/
	protected final Pattern pattern;
	/*** 适配表达式宏匹配{\w*}*/
	protected final Pattern macro;
	/*** 内容适配正则*/
	protected String regex;
	/*** 换行符*/
	protected String line_separator = System.getProperty("line.separator", "\r");

	/**
	 * @param regex 适配正则
	 * @param expression 内容表达式
	 */
	public BaseAdaptor(String regex, String expression) {
		if(null != regex) {
			pattern = Pattern.compile(regex);
		} else {
			pattern = null;
		}
		macro = Pattern.compile("\\{(\\w*)\\}");
		this.regex = regex;
		this.expression = expression;
		initDictionary(null);
	}

	/**
	 * 进程处理.
	 * @param result 执行器返回的结果
	 * @return 适配处理后的内容 
	 */
	public ProcessResult process(ProcessResult result) {
		try {
			// 结果添加命令行内容支持
			this.dictionary.put("command", Arrays.toString(result.getCommand().deal(result.getParams())));
		} catch(Exception e) {
			logger.error("add deal command to dictionary failed!{}", e.getMessage());
		}
		if(null != result) {
			//处理数据
			String[] results = result.getResult();
			//默认去除首尾空白符
			for (int i = 0; i < results.length; i++) {
				result.setResult(i, results[i].trim());
			}
		}
		return result;
	}

	/**
	 * @param config 配置文件
	 */
	protected void initDictionary(String config) {
		dictionary.put("newline", line_separator);
		SimpleDateFormat sdf = new SimpleDateFormat();
		sdf.applyPattern("yyyy-MM-dd");
		dictionary.put("date", sdf.format(System.currentTimeMillis()));
	}

	/**
	 * @param regex 数据内容适配正则
	 */
	public void setRegex(String regex) {
		this.regex = regex;
	}

	/**
	 * 根据数据内容表达式处理数据
	 * @param result 数据内容
	 * @param values [填充数据]
	 * @return 已处理的数据
	 */
	protected String processByExpression(String result, String...values) {
		String content = result;
		dictionary.put("result", content);
		if(this.expression != null && !"".equals(expression)) {
			//根据表达式处理数据
			Matcher matcher = Pattern.compile("\\{(\\w*)\\}").matcher(expression);
			int curr_match = 0;
			String message = expression;
			try {
				while(matcher.find()) {
					String key = matcher.group(1);
					String macro = matcher.group(0);
					if("".equals(key)) {
						message = message.replaceFirst(Pattern.quote(macro), values[curr_match]);
						curr_match++;
					} else {
						if(key.matches("\\d+")) {
							// 参数下标可能出现长度不一的情况,忽略
							int index = Integer.parseInt(key);
							String value = index < values.length ? values[index] : "";
							message = message.replace(macro, value);
						} else
							message = message.replace(macro, dictionary.get(key));
					}
					content = message;
				}
			} catch (Exception e) {
				//处理内容表达式时出现错误
				logger.error("An error occurred while processing the content expression:{}.{}", expression, e.getMessage());
			}
		}
		return content;
	}
}