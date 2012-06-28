/**
 * 
 */
package com.cattsoft.collect.net.adapter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cattsoft.collect.net.process.ProcessResult;

/**
 * DIG+TRACE 结果CNAME内容处理器.
 * <blockquote>
 * <pre><code>
 * CNAME匹配正则({@link #cnameRegex}): \b\w.*CNAME\b*\s+(.+\b)\.?
 * A记录匹配正则({@link #arecordRegex}): \b\s+\w+\s+A\s+\b
 * {@link #disableArecord} 控制是否忽略A记录
 * 
 * 内容表达式标记支持列表:
 * {result} 原内容
 * {date} 日期
 * {time} 时间
 * {newline} 换行</code></pre>
 * </blockquote>

 * @author ChenXiaohong
 */
public class ProcessCnameAdaptor extends BaseAdaptor {
	/*** CNAME 记录匹配正则*/
	private static String cnameRegex = "\\b\\w.*CNAME\\b*\\s+(.+\\b)\\.?";
	/*** 是否禁用记录存在A记录的CNAME信息,默认为false,不禁用*/
	private boolean disableArecord = false;
	/*** DIG数据A记录匹配正则*/
	private String arecordRegex = "\\b\\s+\\w+\\s+A\\s+\\b";
	/*** 出现多个CNAME时,是否只取最后一个 */
	private boolean extractLast = true;
	
	/**
	 * @param expression 数据表达式
	 */
	public ProcessCnameAdaptor(String expression) {
		this(cnameRegex, expression);
	}

	public ProcessCnameAdaptor() {
		this(cnameRegex, "{result}{newline}");
	}

	/**
	 * @param regex 匹配正则
	 * @param expression 数据表达式
	 */
	public ProcessCnameAdaptor(String regex, String expression) {
		super(regex, expression);
	}

	public ProcessResult process(ProcessResult result) {
		super.process(result);
		if(null == result) {
			return result;
		}
		String[] results = result.getResult();
		Matcher matcher = null;
		result.clean();
		//A记录正则
		Pattern aRecordPattern = Pattern.compile(arecordRegex);
		for (String dig_result : results) {
			//查找是否存在A记录信息
			if(disableArecord && aRecordPattern.matcher(dig_result).find()) {
				//存在A记录时结束当前循环
				continue;
			}
			//查找其它信息
			matcher = pattern.matcher(dig_result);
			while(matcher.find()) {
				result.addResult(processByExpression(matcher.group(1), result.getParams()));
			}
		}
		if(isExtractLast() && result.getResult().length > 1) {
			// 清空记录数据,添加最后的记录
			String last = result.getResult()[result.getResult().length - 1];
			result.clean();
			result.addResult(last);
		}
		return result;
	}
	
	public void setDisableArecord(boolean disableArecord) {
		this.disableArecord = disableArecord;
	}
	
	public boolean isDisableArecord() {
		return disableArecord;
	}
	
	public void setArecordRegex(String arecordRegex) {
		this.arecordRegex = arecordRegex;
	}
	
	public String getArecordRegex() {
		return arecordRegex;
	}
	
	public void setExtractLast(boolean extractLast) {
		this.extractLast = extractLast;
	}
	
	public boolean isExtractLast() {
		return extractLast;
	}
}
