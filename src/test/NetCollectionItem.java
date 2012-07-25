/**
 * 
 */
package test;

import java.io.Serializable;
import java.util.Comparator;

import com.cattsoft.collect.io.utils.StringUtils;

/**
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 * 
 */
public class NetCollectionItem implements Serializable, Comparator<NetCollectionItem>, Comparable<NetCollectionItem> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String name, type;

	/**
	 * @param name
	 * @param type
	 */
	public NetCollectionItem(String name, String type) {
		this.name = name;
		this.type = type;
	}
	
	/**
	 * 
	 */
	public NetCollectionItem() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

	public int compareTo(NetCollectionItem another) {
		if(StringUtils.equals(name, another.getName()) && StringUtils.equals(type, another.getName())) {
			return 0;
		}
		return -1;
	}

	@Override
	public int compare(NetCollectionItem o1, NetCollectionItem o2) {
		if(StringUtils.equals(o1.getName(), o2.getName()) && StringUtils.equals(o1.getName(), o2.getName())) {
			return 0;
		}
		return 0;
	}
}
