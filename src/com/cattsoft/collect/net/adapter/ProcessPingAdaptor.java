/**
 * 
 */
package com.cattsoft.collect.net.adapter;

import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cattsoft.collect.net.process.ProcessResult;

/**
 * Ping 命令内容处理适配器.
 * 处理Ping结果抽取IP地址,等信息
 * 默认(linux)
 * <blockquote><pre><code>
 * 内容匹配正则表达式
 * linux:
 *  ^.*?([\w\d\.]+)\s\(([\d\.]+).*?(\d+)[\w\s]+,\s+(\d+)[\w\s]+,\s+(\d+)%[\w\s]+,.*?(\d+).*$
 *  scamper:
 *  ^.*?([\d\.]+).*?([\d\.]+).*?(\d+)[\w\s]+,\s+(\d+)[\w\s]+,\s+(\d+)%.*?([\d\.]+)\s+ms$
 * windows(中文):
 *  ^.*\s\w+\s(.*?)\s\[(.*?)\].*?=\s+(\d+).*?(\d+).*\d.*\((\d+)%(.*\d+ms.*\d+ms.*?(\d+)ms)?
 * 
 * 内容表达式标记支持列表:
 * {result} 原内容
 * {} {0} 等表达形式将填入参数,{} 将按顺序填充,{0} 方式按参数下标填充
 * {ip} IP地址
 * {purl} 参数网址.此处获取参数Url时为参数的第1个,以后如果有发动,需要重新设置
 * {url} 报文网址
 * {loss} 丢失率
 * {trans} 发送数据
 * {receive} 响应数据
 * {time} 用时(ms)
 * {date} 当前日期
 * {dtime} 当前时间
 * {newline} 换行
 * </code></pre></blockquote> 
 * @author ChenXiaohong
 */
public class ProcessPingAdaptor extends BaseAdaptor {
	/*** 最大丢失率(默认99)*/
	private int maxLoss = 99;
	
	public ProcessPingAdaptor(String expression) {
		this("^.*?([\\w\\d\\.]+)\\s\\(([\\d\\.]+).*?(\\d+)[\\w\\s]+,\\s+(\\d+)[\\w\\s]+,\\s+(\\d+)%[\\w\\s]+,.*?(\\d+).*$",
				expression);
		// windows
//		this("^.*\\s\\w+\\s(.*?)\\s\\[(.*?)\\].*?=\\s+(\\d+).*?(\\d+).*\\d.*\\((\\d+)%(.*\\d+ms.*\\d+ms.*?(\\d+)ms)?", expression);
	}

	/**
	 * @param regex 正则表达式
	 * @param expression 内容表达式
	 */
	public ProcessPingAdaptor(String regex, String expression) {
		super(regex, expression);
	}

	public ProcessResult process(ProcessResult result) {
		super.process(result);
		if(null == result)
			return result;
		SimpleDateFormat sdf = new SimpleDateFormat();
		//: 数据处理
		this.dictionary.put("loss", "100"); //默认设置为最大值(100)
		String purl = result.getParams()[0];
		// 参数Url
		this.dictionary.put("purl", purl);
		// 更新数据
		String[] result_arr = result.getResult();
		for (int i = 0; i < result_arr.length; i++) {
			String new_result = "";
			
			Matcher result_matcher = pattern.matcher(result_arr[i].replaceAll("[\r\n]", ""));
			this.dictionary.put("result", result_arr[i]);
			if(result_matcher.find()) {
				this.dictionary.put("url", result_matcher.group(1));
				this.dictionary.put("ip", result_matcher.group(2));
				this.dictionary.put("trans", result_matcher.group(3));
				this.dictionary.put("receive", result_matcher.group(4));
				//丢失率
				this.dictionary.put("loss", (!"".equals(result_matcher.group(5))) ? result_matcher.group(5) : "100");
				//linux groupcount the 6
				int time_index = (result_matcher.groupCount() > 6) ? 7 : 6;
				//用时
				this.dictionary.put("time", (null != result_matcher.group(time_index)) ? result_matcher.group(time_index) : "");
				
			}
			//当前时间
			sdf.applyPattern("HH:mm:ss");
			this.dictionary.put("dtime", sdf.format(System.currentTimeMillis()));
			//丢失率
			int loss = Integer.parseInt(this.dictionary.get("loss"));
			if(loss <= maxLoss) {
				new_result = expression;
				// 此处获取参数Url时为参数的第1个,以后如果有发动，需要在此处重新设置
				//适配表达式
				Matcher matcher = macro.matcher(expression);
				// 匹配{}情况设置值
				int cur_matcher_empkey = 0;
				while(matcher.find()) {
					String key = matcher.group(1);
					if("".equals(key)) {
						//下标识别
						new_result = new_result.replaceFirst(Pattern.quote(matcher.group(0)), result.getParams()[cur_matcher_empkey]);
						cur_matcher_empkey++;
					} else if(key.matches("\\d+")) {
						new_result = new_result.replaceAll(Pattern.quote(matcher.group(0)), result.getParams()[Integer.parseInt(key)]);
					} else {
						new_result = new_result.replace(matcher.group(0), (null != dictionary.get(key) ? dictionary.get(key) : ""));
					}
				}
			}
			result.setResult(i, new_result);
			//:~
		}
		return result;
	}
	
	/**
	 * @param maxLoss 设置数据最大丢失率
	 */
	public void setMaxLoss(int maxLoss) {
		this.maxLoss = maxLoss;
	}
	
	public int getMaxLoss() {
		return maxLoss;
	}
}
