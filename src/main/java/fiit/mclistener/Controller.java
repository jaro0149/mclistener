package fiit.mclistener;

public class Controller {

	private Window window;;
	private PcapMachine pcapMachine;
	
	public Controller(String titleAddon) {
		initialiseWindow(titleAddon);
		initialisePcapMachine();
		addActionRegisterGroup();
		addActionRemoveGroup();		
	}
	
	private void initialiseWindow(String titleAddon) {
		window = new Window(titleAddon);
	}
	
	private void initialisePcapMachine() {
		try {
			pcapMachine = new PcapMachine(window);
		} catch (Exception ex) {
			new ErrorMessage(ex.getMessage());
		}
	}
	
	private void addActionRegisterGroup() {
		window.addActionRegisterGroup(e -> {
			try {
				String multicastGroup = window.getMulticastGroup();
				pcapMachine.registerMulticastGroup(multicastGroup);
				window.addMulticastGroup(multicastGroup);
			} catch(Exception ex) {
				new ErrorMessage(ex.getMessage());
			}
		});
	}
	
	private void addActionRemoveGroup() {
		window.addActionRemoveGroup(e -> {
			try {
				String multicastGroup = window.getSelectedGroup();
				pcapMachine.removeMulticastGroup(multicastGroup);
				window.removeMulticastGroup(multicastGroup);
			} catch(Exception ex) {
				new ErrorMessage(ex.getMessage());
			}			
		});
	}
	
	public void setVisible(boolean visible) {
		window.setVisible(visible);
	}
}
