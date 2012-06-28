/**
 * 
 */
package com.cattsoft.collect.manage.ui;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import com.cattsoft.collect.manage.data.ConfigUtils;

/** 关于
 * @author ChenXiaohong
 *
 */
public class AboutDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	public AboutDialog() {
		setTitle("关于");
		pack();
		setResizable(false);
		setModal(true);
		setLayout(null);
		setSize(372, 240);
		
		JLabel lbl_about = new JLabel("<html><b style=\"font-size:24\">终端采集管理程序</b> v3.0.0</html>");
		lbl_about.setBounds(new Rectangle(80, 20, getWidth(), 50));
		add(lbl_about);
		
		JLabel lbl_author = new JLabel(ConfigUtils.getProp().getProperty("author", "大唐电信"));
		lbl_author.setBounds(new Rectangle(186, 60, 120, 50));
		add(lbl_author);
		
		JButton btn_reset = new JButton("删除配置");
		btn_reset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int flag = JOptionPane.showConfirmDialog(null, "是否重置程序配置信息?", "重置", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if(flag == JOptionPane.YES_OPTION) {
					ConfigUtils.reset();
				}
			}
		});
		btn_reset.setBounds(new Rectangle(240, 140, 84, 24));
		
		add(btn_reset);
		
		UIUtils.setCenter(this);
		UIUtils.addEscapeListener(this);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		
		setVisible(true);
	}
}
