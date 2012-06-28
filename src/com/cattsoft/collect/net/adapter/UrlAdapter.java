/**
 * 
 */
package com.cattsoft.collect.net.adapter;

import java.util.regex.Matcher;

import com.cattsoft.collect.net.process.ProcessResult;


/**
 * 读取器URL内容适配处理器. 通过正则表达式对URL截取域名信息 <blockquote>
 * 
 * <pre>
 * <code>当前:^(http[s]?://)?([\w\.\-]+[^/])
 * 其它:(?<=http[s]?://)[\w\.\-]+[^/]
 * </code>
 * </pre>
 * 
 * </blockquote>
 * 
 * @author ChenXiaohong
 * 
 */
public class UrlAdapter extends BaseAdaptor {
	public UrlAdapter() {
		this("^(http[s]?://)?([\\w\\.\\-]+[^/])");
	}
	
	/**
	 * @param regex URL正则
	 */
	public UrlAdapter(String regex) {
		super(regex, "{result}");
	}
	
	public ProcessResult process(ProcessResult result) {
		super.process(result);
		//处理参数
		for (int i = 0; i < result.getParams().length; i++) {
			Matcher matcher = pattern.matcher(result.getParams()[i]);
			if(matcher.find()) {
				//更新
				result.setParam(i, matcher.group(2));
			}
		}
		return result;
	}
}
