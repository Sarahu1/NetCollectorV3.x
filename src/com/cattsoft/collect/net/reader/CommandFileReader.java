/**
 * 
 */
package com.cattsoft.collect.net.reader;

import java.util.LinkedList;
import java.util.List;

import com.cattsoft.collect.net.actuator.Command;
import com.cattsoft.collect.net.adapter.BaseAdaptor;
import com.cattsoft.collect.net.process.ProcessResult;

/**
 * 命令集文件读取器.
 * 按行读取数据文件并创建命令集(调用{@link #getCommand()}方法创建),
 * 参数需要进行分割时设置split值对参数进行分割.
 * <blockquote><pre>
 * <code>setSplit(symbol) 以逗号对参数行进行分割.
 * setDirectory(String directory) 设置命令工作目录
 *</code></pre>
 * </blockquote>
 * @author ChenXiaohong
 *
 */
public class CommandFileReader extends BasicFileReader {
	/*** 命令集*/
	private Command command;
	/*** 命令工作目录*/
	private String directory = "";
	/*** 命令模板*/
	private final String[] template;
	/*** 命令参数适配器*/
	private List<BaseAdaptor> adapters = new LinkedList<BaseAdaptor>();
	/*** 命令行参数分割符*/
	private String split = "";
	
	/**
	 * @param template 命令模板
	 * @param filePath 数据文件路径
	 * @param skipPath 断点记录文件路径
	 */
	public CommandFileReader(String[] template, String filePath, String skipPath) {
		super(filePath, skipPath);
		this.template = template;
	}
	
	/**
	 * 读取数据文件并创建命令集
	 * @return 命令集
	 */
	public Command getCommand() {
		//根据模板创建命令集
		command = new Command(template);
		//设置命令目录
		command.setDirectory(this.directory);
		//读取文件行
		List<String> lines = readLines();
		for (String line : lines) {
			String[] params = new String[]{line};
			if(!"".equals(split)) {
				//参数行分割
				params = line.split(split);
			}
			//处理数据内容
			ProcessResult result = new ProcessResult("", command, template, params);
			for (BaseAdaptor adapter : adapters) {
				result = adapter.process(result);
			}
			//添加命令参数
			command.put(result.getParams());
		}
		// 标记断点位置
		command.setSkip(skip);
		//返回
		return command;
	}
	
	/**
	 * 设置工作目录
	 * @param directory 目录 
	 */
	public void setDirectory(String directory) {
		this.directory = directory;
	}
	
	/**
	 * @return 命令当前工作目录
	 */
	public String getDirectory() {
		return command.getDirectory();
	}
	
	public void setAdapters(List<BaseAdaptor> adapters) {
		this.adapters = adapters;
	}
	
	public List<BaseAdaptor> getAdapters() {
		return adapters;
	}
	
	/**
	 * @param split 分割符
	 */
	public void setSplit(String split) {
		this.split = split;
	}
	
	public String getSplit() {
		return split;
	}
}
