/**
 * 
 */
package com.cattsoft.collect.net.actuator;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基本命令集.
 * @author ChenXiaohong
 */
public class Command {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/*** 命令模板*/
	private final String[] template;
	/*** 命令执行结果*/
	private String[] result;
	/*** 命令工作目录*/
	private String directory;
	/*** 命令模板内容替换正则.解释命令行*/
	private Pattern pattern = Pattern.compile("\\{(\\d*)\\}");
	/*** 命令参数列表,参数列表需要注意溢出问题*/
	private List<String[]> parameters;
	/*** 命令文件断点位置 */
	private int skip = 0;

	/**
	 * @param template 命令行模板
	 */
	public Command(String... template) {
		this.template = template;
		parameters = new LinkedList<String[]>();
	}

	/**
	 * 获取一组命令参数
	 * @param index 下标
	 * @return 参数列表
	 */
	public String[] getParams(int index) {
		return parameters.get(index);
	}

	/**
	 * @return 命令执行结果
	 */
	public String[] getResult() {
		return result;
	}

	/**
	 * @param result 更新结果集
	 */
	public void setResult(String... result) {
		this.result = result;
	}

	/**
	 * @return 命令当前工作目录
	 */
	public String getDirectory() {
		return directory;
	}

	/**
	 * @param directory 设置命令工作目录
	 */
	public void setDirectory(String directory) {
		this.directory = directory;
	}
	
	/**
	 * 套用模板,解析一组命令
	 * @return 命令行
	 */
	public String[] deal(String... params) {
		String[] cmdline = template.clone();
		//当前{}值匹配下标
		int cur_match = 0;
		//循环命令参数行
		for (int i = 0; i < cmdline.length; i++) {
			//匹配参数
			Matcher matcher = pattern.matcher(cmdline[i]);
			//查找是否匹配替换规则
			if(matcher.find()) {
				//获取需要替换的参数下标值
				String group = matcher.group(1);
				//如果设置有参数值下标则使用下标值,否则由当前{}匹配值下标决定值
				int index = Integer.parseInt("".equals(group) ? String.valueOf(cur_match++) : group);
				try {
					//替换值
					cmdline[i] = params[index];
				} catch (ArrayIndexOutOfBoundsException e) {
					//命令模板下标配置与参数长度不一致,请检查命令行
					throw new IllegalArgumentException("Subscript in the command template configuration is inconsistent " +
							"with the argument length, please check the command line!", e);
				} catch (Exception e) {
					//命令行解析出现错误
					logger.error("Command line parsing error!{}", e.getMessage());
				}
			}
		}
		return cmdline;
	}
	
	/**
	 * 套用模板,解析一组命令参数
	 * @param index 参数下标
	 * @return 命令行
	 */
	public String[] deal(int index) {
		return deal(parameters.get(index));
	}
	
	/** 添加一组命令参数
	 * @param params 参数
	 */
	public boolean put(String... params) {
		return parameters.add(params);
	}
	
	/**
	 * @param params 添加一个命令参数列表
	 * @return 是否成功
	 */
	public boolean putAll(Collection<? extends String[]> params) {
		return parameters.addAll(params);
	}
	
	/**
	 * 更新一组命令参数
	 * @param index 下标
	 * @param params 参数
	 * @return 更新后的参数值
	 */
	public String[] set(int index, String... params) {
		return parameters.set(index, params);
	}
	
	/**
	 * 删除一组命令参数
	 * @param index 下标
	 */
	public String[] remove(int index) {
		return parameters.remove(index);
	}
	
	/** 删除一组命令参数
	 * @param params 参数
	 */
	public boolean remove(String... params) {
		return parameters.remove(params);
	}
	
	/**
	 * 清空命令参数列表
	 */
	public void clean() {
		parameters.clear();
	}
	
	/**
	 * @return 当前参数列表大小
	 */
	public int size() {
		return parameters.size();
	}
	
	/**
	 * @return 当前命令模板
	 */
	public String[] getTemplate() {
		return template;
	}
	
	/**
	 * @param skip 数据文件断点位置
	 */
	public void setSkip(int skip) {
		this.skip = skip;
	}
	
	/**
	 * @return 数据文件断点位置
	 */
	public int getSkip() {
		return skip;
	}
}