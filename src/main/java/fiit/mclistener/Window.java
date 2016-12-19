package fiit.mclistener;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial")
public class Window extends JFrame {

	private static final String TITLE = "Multicast Listener";
	private static final String ERROR_EMPTY_COMBOBOX = "The empty combo-box item was selected.";
	private static final int HEIGHT = 600;
	private static final int WIDTH = 900;
	private static final String DEFAULT_ADDRESS = "233.10.47.10";
	
	// Labels
	private static final String[] LABELS = { "Multicast group:", "Registered groups:", "Messages:" };
	private final JLabel lblMutlicastGroup = new JLabel(LABELS[0]);
	private final JLabel lblRegisteredGroups = new JLabel(LABELS[1]);
	private final JLabel lblMessages = new JLabel(LABELS[2]);
	
	// Text field
	private final JTextField txtMulticastGroup = new JTextField(DEFAULT_ADDRESS);
	
	// Buttons	
	private static final String[] BUTTONS = {"Register","Remove"};
	private final JButton btnRegister = new JButton(BUTTONS[0]);
	private final JButton btnRemove = new JButton(BUTTONS[1]);
	
	// Combo-box
	@SuppressWarnings("rawtypes")
	private final JComboBox comboRegisteredGroups = new JComboBox();
	
	// Separator
	private final JSeparator textSeparator = new JSeparator(SwingConstants.HORIZONTAL);
	
	// Text area
	private final JTextArea areaMessages = new JTextArea();
	
	public Window(String titleAddon) {
		
		// Configuration
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(WIDTH, HEIGHT);
		setResizable(true);
		setTitle(TITLE + " " + titleAddon);
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(dim.width / 2 - this.getSize().width / 2, dim.height / 2 - this.getSize().height / 2);

		// Layout
		GridBagLayout gridLayout = new GridBagLayout();
		gridLayout.columnWidths = new int[] { 20, 80, 200, 80, 20 };
		gridLayout.columnWeights = new double[] { 3.0, 1.0, 1.0, 1.0, 3.0 };
		setLayout(gridLayout);
		
		// Labels
		lblMutlicastGroup.setFont(new Font("Times New Roman", Font.BOLD, 16));
		lblRegisteredGroups.setFont(new Font("Times New Roman", Font.BOLD, 16));
		lblMessages.setFont(new Font("Times New Roman", Font.BOLD, 16));

		add(lblMutlicastGroup, new GridBagTemplate(GridBagConstraints.WEST, 0, 1, 10, 10, 10, 10, 1));
		add(lblRegisteredGroups, new GridBagTemplate(GridBagConstraints.WEST, 1, 1, 10, 10, 10, 10, 1));
		add(lblMessages, new GridBagTemplate(GridBagConstraints.WEST, 3, 1, 10, 10, 10, 10, 1));
		
		// Buttons
		btnRegister.setFont(new Font("Times New Roman", Font.PLAIN, 14));
		btnRemove.setFont(new Font("Times New Roman", Font.PLAIN, 14));
		
		add(btnRegister, new GridBagTemplate(GridBagConstraints.WEST, 0, 3, 10, 10, 10, 10, 1));
		add(btnRemove, new GridBagTemplate(GridBagConstraints.WEST, 1, 3, 10, 10, 10, 10, 1));
		
		// Text field
		txtMulticastGroup.setFont(new Font("Times New Roman", Font.PLAIN, 16));
		txtMulticastGroup.setHorizontalAlignment(SwingConstants.CENTER);
		add(txtMulticastGroup, new GridBagTemplate(GridBagConstraints.WEST, 0, 2, 10, 10, 10, 10, 1));
		
		// Separator
		add(textSeparator, new GridBagTemplate(GridBagConstraints.WEST, 2, 1, 10, 10, 10, 10, 3));
		
		// Combo-box
		comboRegisteredGroups.setFont(new Font("Times New Roman", Font.PLAIN, 14));
		comboRegisteredGroups.setMaximumRowCount(10);
		add(comboRegisteredGroups, new GridBagTemplate(GridBagConstraints.WEST, 1, 2, 5, 10, 10, 10, 1));
		
		// Text area
		areaMessages.setFont(new Font("Courier New", Font.PLAIN, 14));
		areaMessages.setEditable(true);
		JScrollPane scrollDebuggingWindow = new JScrollPane(areaMessages);
		add(scrollDebuggingWindow, new GridBagTemplate(GridBagConstraints.WEST, 4, 1, 10, 10, 20, 10, 3, 1.0));
		
	}
	
	public String getMulticastGroup() {
		return txtMulticastGroup.getText().trim();
	}
	
	public String getSelectedGroup() {
		int selectedIndex = comboRegisteredGroups.getSelectedIndex();
		if(selectedIndex!=-1) {
			return comboRegisteredGroups.getSelectedItem().toString().trim();
		} else {
			throw new UnsupportedOperationException(ERROR_EMPTY_COMBOBOX);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void addMulticastGroup(String multicastGroup) {
		SwingUtilities.invokeLater(() -> {
			comboRegisteredGroups.addItem(multicastGroup);
		});
	}
	
	public void removeMulticastGroup(String multicastGroup) {
		SwingUtilities.invokeLater(() -> {
			comboRegisteredGroups.removeItem(multicastGroup);
		});
	}
	
	public void addLineToMessagesArea(String text) {
		SwingUtilities.invokeLater(() -> {
			areaMessages.append(text);
			areaMessages.append("\n");
		});
	}
	
	public void addActionRegisterGroup(ActionListener actionListener) {
		btnRegister.addActionListener(actionListener);
	}
	
	public void addActionRemoveGroup(ActionListener actionListener) {
		btnRemove.addActionListener(actionListener);
	}
	
}
