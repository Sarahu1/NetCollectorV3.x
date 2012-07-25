/**
 * 
 */
package test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class TestIndex {
	public static void main(String[] args) {
		List<NetCollectionItem> items = new ArrayList<NetCollectionItem>();
		items.add(new NetCollectionItem("北京联通", "常规"));
		
		NetCollectionItem item = new NetCollectionItem("北京联通", "常规");
		
		for (NetCollectionItem netCollectionItem : items) {
			System.out.println(netCollectionItem.compareTo(item));
		}
		
//		System.out.println(Collections.binarySearch(items, item, new NetCollectionItem()));;
		System.out.println(items.indexOf(item));
	}
}
