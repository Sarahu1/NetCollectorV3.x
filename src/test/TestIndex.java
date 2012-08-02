/**
 * 
 */
package test;

import java.text.DecimalFormat;

/**
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class TestIndex {
	public static void main(String[] args) {
		DecimalFormat trafficDF = new DecimalFormat("0.0KB");
		System.out.println(trafficDF.format(-1));
	}
}
