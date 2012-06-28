/**
 * 
 */
package com.cattsoft.collect.net.adapter;

import com.cattsoft.collect.net.process.ProcessResult;

/**
 * 进程结果内容基础适配处理器.
 * 默认将对内容删除前后空白符,包括回车换行
 * <blockquote><pre>
 * <code>
 * 内容表达式标记支持列表:
 * {result} 原内容
 * {date} 日期
 * {time} 时间
 * {newline} 换行
 * </code>
 * </pre></blockquote>
 * @author ChenXiaohong
 */
public class BasicResultAdaptor extends BaseAdaptor {
	
	public BasicResultAdaptor() {
		this("");//默认对内容不进行处理
	}
	
	/**
	 * @param expression 适配表达式
	 */
	public BasicResultAdaptor(String expression) {
		super(null, expression);
	}
	
	/**
	 * @param regex 适配正则
	 * @param expression 适配表达式
	 */
	public BasicResultAdaptor(String regex, String expression) {
		super(regex, expression);
	}
	
	public ProcessResult process(ProcessResult process) {
		super.process(process);
		for (int i = 0; i < process.getResult().length; i++) {
			process.setResult(i, processByExpression(process.getResult()[i]));
		}
		return process;
	}
}
