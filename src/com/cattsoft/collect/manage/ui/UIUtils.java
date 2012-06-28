package com.cattsoft.collect.manage.ui;

import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;

import com.cattsoft.collect.manage.data.ConfigUtils;

/**
 * @author Xiaohong
 *
 */
public class UIUtils {
	public static void addJTextComponent(Container root, JTextComponent component, String value, boolean editable, Rectangle rec) {
		component.setText(value);
		component.setEditable(editable);
		component.setBounds(rec);
		root.add(component);
	}

	/** 设置文本框只接受数字
	 * @param txt 文本框
	 */
	public static void setNumberDocument(JTextComponent txt) {
		txt.setDocument(new PlainDocument() {
			private static final long serialVersionUID = 1L;
			/* 限定输入字符类型
			 * @see javax.swing.text.PlainDocument#insertString(int, java.lang.String, javax.swing.text.AttributeSet)
			 */
			public void insertString(int offs, String str, AttributeSet attr)
					throws BadLocationException {
				if (str == null) {
					return;
				}
				if(!str.matches("\\d+"))
					return;
				super.insertString(offs, str, attr);
			}
		});
	}
	
	/** 文本框获取焦点时选择全部内容
	 * @param component
	 */
	public static void addFocusSelectAll(JTextComponent component) {
		component.addFocusListener(new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				super.focusGained(e);
				((JTextComponent)e.getSource()).selectAll();
			}
		});
	}
	
	/** ESC 键事件
	 * @param dialog
	 */
	public static void addEscapeListener(final JDialog dialog) {
		ActionListener escListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
			}
		};
		dialog.getRootPane().registerKeyboardAction(escListener,
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);
	}
	
	public static void addEscapeListener(final JFrame frame) {
		ActionListener escListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
			}
		};
		frame.getRootPane().registerKeyboardAction(escListener,
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);
	}
	
	public static void addEntryEventListener(JComponent compon, AbstractButton button) {
		button.doClick();
	}
	
	/** 发送关闭事件
	 * @param source 窗口
	 */
	public static void dispatchCloseEvent(Window source) {
		if(null != source) {
			source.dispatchEvent(new WindowEvent(source, WindowEvent.WINDOW_CLOSING));
		}
	}
	
	/** 设置窗口居中显示
	 * @param window 窗口
	 */
	public static void setCenter(Window window) {
		Point center = new Point();
		center.x = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth() / 2 - window.getWidth() / 2;
		center.y = (int) Toolkit.getDefaultToolkit().getScreenSize().getHeight() / 2 - window.getHeight() / 2;
		window.setLocation(center);
	}
	
	/** 获取终端对照表
	 * @return 终端编号<终端名称,地址>
	 */
	public static Object[][] getTerminalTable(String path) {
		List<String[]> terminal_list = new LinkedList<String[]>();
		File file = null;
		Properties props = ConfigUtils.getProp();
		if(null == path || "".equals(path)) {
			String filename = props.getProperty("terminal_tab", "terminal");
			// 默认读取根目录文件
			String catalog = ConfigUtils.getCatalog();
			file = new File(catalog + File.separator + filename);
			if(!file.exists()) {
				file = new File(filename);
				if(!file.exists()) {
					try {
						file = new File(UIUtils.class.getResource(filename).toURI());
					} catch (Exception e) {
					}
				}
			}
		} else {
			file = new File(path);
			props.put("terminal_tab", path);
			ConfigUtils.store(props, "terminal tab set");
		}
		// 从文件读取数据,按行对数据进行分割
		try {
			FileInputStream fis = new FileInputStream(file);
			InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
			BufferedReader br = new BufferedReader(isr);
			String line;
			while((line = br.readLine()) != null) {
				try {
					String[] terinfo = line.trim().split(",");
					if(terinfo.length != 3) {
						String[] terstr = line.split("=");
						String[] name_ip = terstr[1].split("@");
						terinfo = new String[]{terstr[0], name_ip[0], name_ip[1]};
					}
					terminal_list.add(terinfo);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			br.close();
			isr.close();
			fis.close();
		} catch (Exception e) {
			//
		}
		return terminal_list.toArray(new Object[0][0]);
	}
}
