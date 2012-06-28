/**
 * 
 */
package com.cattsoft.collect.net.process;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.cattsoft.collect.net.actuator.Command;

/**
 * 执行器返回的结果.
 * 
 * @author ChenXiaohong
 */
public class ProcessResult implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;
	/*** 命令行模板 */
	private final String[] template;
	/*** 命令行参数 */
	private String[] params;
	/*** 当前命令集*/
	private final Command command;
	/*** 单个命令运行结果 */
	private List<String> result = new LinkedList<String>();

	/**
	 * @param result
	 *            结果
	 * @param template
	 *            命令模板
	 * @param params
	 *            命令参数
	 */
	public ProcessResult(String result, Command command, String[] template, String[] params) {
		this.result.add(result);
		this.command = command;
		this.template = template;
		this.params = params;
	}
	
	/**
	 * @param result
	 *            结果数组
	 * @param template
	 *            命令模板
	 * @param params
	 *            命令参数
	 */
	public ProcessResult(String[] result, Command command, String[] template, String[] params) {
		Collections.addAll(this.result, result);
		this.command = command;
		this.template = template;
		this.params = params;
	}

	/**
	 * @return 命令运行参数
	 */
	public String[] getParams() {
		return params;
	}
	
	/** 更新参数.
	 * @param index 下标
	 * @param value 值
	 */
	public void setParam(int index, String value) {
		this.params[index] = value;
	}

	/**
	 * @return 结果内容
	 */
	public String[] getResult() {
		return result.toArray(new String[] {});
	}

	/**
	 * @param index
	 *            下标
	 * @param result
	 *            更新结果内容
	 */
	public void setResult(int index, String result) {
		this.result.set(index, result);
	}

	/**
	 * 添加结果内容
	 * 
	 * @param result
	 *            内容
	 * @return 是否添加成功
	 */
	public boolean addResult(String result) {
		return this.result.add(result);
	}
	
	/**
	 * 清空结果内容列表
	 */
	public void clean() {
		result.clear();
	}

	/**
	 * @return 当前命令模板
	 */
	public String[] getTemplate() {
		return template;
	}
	
	/**
	 * @return 当前命令集
	 */
	public Command getCommand() {
		return command;
	}

	public ProcessResult clone() {
		ProcessResult clone = null;
		try {
			clone = (ProcessResult) super.clone();
		} catch (Exception e) {
			//
		}
		return clone;
	}

	/**
	 * 深度克隆
	 * 
	 * @return 深度克隆后的对象
	 */
	public ProcessResult deepClone() {
		ProcessResult clone = null;
		if (this != null) {
			try {
				// 写出序列化对象
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos);
				oos.writeObject(this);
				oos.close();
				// 读取序列化对象
				ByteArrayInputStream bais = new ByteArrayInputStream(
						baos.toByteArray());
				ObjectInputStream ois = new ObjectInputStream(bais);

				clone = (ProcessResult) ois.readObject();
				ois.close();
			} catch (Exception e) {
				// 克隆失败
			}
		}
		return clone;
	}

	/**
	 * 假克隆. <blockquote>
	 * 
	 * <pre>
	 * <code>
	 * 具体实现:
	 * new ProcessResult(this.getResult(), this.getTemplate(), this.getParams());
	 *  </code>
	 * </pre>
	 * 
	 * </blockquote>
	 * 
	 * @return new 对象
	 */
	public ProcessResult fakeClone() {
		return new ProcessResult(this.getResult().clone(), this.command, 
				this.getTemplate().clone(), this.getParams().clone());
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		try {
			sb.append("result:").append(result).append(", ");
			sb.append("command:" + (null != command ? Arrays.toString(command.deal(params)) : "[]"));
		} catch (Exception e) {
			//
		}
		return sb.toString();
	}
}