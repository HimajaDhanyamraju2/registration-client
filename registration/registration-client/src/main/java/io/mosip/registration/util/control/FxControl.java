/**
 * 
 */
package io.mosip.registration.util.control;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.util.List;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.ClientApplication;
import io.mosip.registration.controller.GenericController;
import io.mosip.registration.controller.Initialization;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.enums.FlowType;
import io.mosip.registration.validator.RequiredFieldValidator;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * 
 * Control Type will give high level controls for fields TextField,CheckBox,
 * DropDown,DropDown, Buttons, document type, Biometric Type
 * 
 * It also provides a features to copy,disable and visible
 * 
 * @author YASWANTH S
 *
 */
public abstract class FxControl  {

	protected static final Logger LOGGER = AppConfig.getLogger(FxControl.class);
	private static final String loggerClassName = "FxControl";

	protected UiFieldDTO uiFieldDTO;
	protected FxControl control;
	public Node node;

	protected AuditManagerService auditFactory;
	protected RequiredFieldValidator requiredFieldValidator;


	/**
	 * Build Error code, title and fx Element Set Listeners Set Actione events
	 * 
	 * @param uiFieldDTO field information
	 */
	public abstract FxControl build(UiFieldDTO uiFieldDTO);

	/**
	 *
	 * @param node
	 */
	public abstract void setListener(Node node);

	/**
	 * 
	 * Set Data into Registration DTO
	 * 
	 * @param data value
	 */
	public abstract void setData(Object data);

	/**
	 * 
	 * Fill Data into fx element
	 * 
	 * @param data value
	 */
	public abstract void fillData(Object data);

	/**
	 * Get Value from fx element
	 * 
	 * @return Value
	 */
	public abstract Object getData();

	/**
	 * Check value is valid or not
	 * 
	 * @return boolean is valid or not
	 */
	//public abstract boolean isValid(Node node);

	/**
	 *
	 * @param data
	 */
	public abstract void selectAndSet(Object data);

	/**
	 * Check value is valid or not
	 *
	 * @return boolean is valid or not
	 */
	public abstract boolean isValid();

	/**
	 * Check value is empty
	 *
	 * @return boolean is valid or not
	 */
	public abstract boolean isEmpty();

	/**
	 *
	 * @param langCode
	 * @return
	 */
	public abstract List<GenericDto> getPossibleValues(String langCode);


	/**
	 * Disable the field
	 */
	public void disable(Node node, boolean isDisable) {

		node.setDisable(isDisable);

	}

	/**
	 * Refresh the field
	 */
	public void refresh() {
		boolean isFieldVisible =  isFieldVisible(uiFieldDTO);
		if(!isFieldVisible) {
			switch (uiFieldDTO.getType()) {
				case "documentType":
					getRegistrationDTo().removeDocument(uiFieldDTO.getId());
					break;
				case "biometricsType":
					List<String> requiredAttributes = requiredFieldValidator.getRequiredBioAttributes(uiFieldDTO, getRegistrationDTo());
					for(String bioAttribute : uiFieldDTO.getBioAttributes()) {
						if(!requiredAttributes.contains(bioAttribute))
							getRegistrationDTo().clearBIOCache(uiFieldDTO.getId(), bioAttribute);
					}
					break;
				default:
					getRegistrationDTo().removeDemographicField(uiFieldDTO.getId());
					break;
			}
		}
		visible(this.node, isFieldVisible(uiFieldDTO));
	}

	/**
	 * Hide the field
	 */
	public void visible(Node node, boolean isVisible) {
		node.setVisible(isVisible);
		node.setManaged(isVisible);
	}

	/**
	 *
	 */
	public void refreshFields() {
		LOGGER.info(loggerClassName, APPLICATION_NAME, APPLICATION_ID, "Refreshing fields from fx control");
		GenericController genericController = ClientApplication.getApplicationContext().getBean(GenericController.class);
		genericController.refreshFields();
	}

	/**
	 *
	 * @return
	 */
	public boolean canContinue() {
		//field is not visible, ignoring valid value and isRequired check
		if(!isFieldVisible(this.uiFieldDTO)) {
			return true;
		}

		if (requiredFieldValidator == null) {
			requiredFieldValidator = ClientApplication.getApplicationContext().getBean(RequiredFieldValidator.class);
		}

		try {
			boolean isValid = isValid();
			if(isValid)
				return true;

			boolean isRequiredField = requiredFieldValidator.isRequiredField(this.uiFieldDTO, getRegistrationDTo());
			if(isEmpty() && !isRequiredField)
				return true;

			if(getRegistrationDTo().getFlowType() == FlowType.UPDATE
				&& !getRegistrationDTo().getUpdatableFields().contains(this.uiFieldDTO.getId())) {
				LOGGER.error("canContinue check on, {} is non-updatable ignoring", uiFieldDTO.getId());
				return true;
			}
		} catch (Exception exception) {
			LOGGER.error("Error checking RequiredOn for field : " + uiFieldDTO.getId(), exception);
		}
		return false;
	}

	/**
	 *
	 * @param schema
	 * @return
	 */
	protected String getMandatorySuffix(UiFieldDTO schema) {
		String mandatorySuffix = RegistrationConstants.EMPTY;
		switch (getRegistrationDTo().getFlowType()) {
			case UPDATE:
				if (getRegistrationDTo().getUpdatableFields().contains(schema.getId())) {
					mandatorySuffix = schema.isRequired() ? RegistrationConstants.ASTRIK : RegistrationConstants.EMPTY;
				}
				break;

			case CORRECTION:
			case NEW:
				mandatorySuffix = schema.isRequired() ? RegistrationConstants.ASTRIK : RegistrationConstants.EMPTY;
				break;
		}
		return mandatorySuffix;
	}

	/**
	 *
	 * @param id
	 * @param titleText
	 * @param styleClass
	 * @param isVisible
	 * @param prefWidth
	 * @return
	 */
	protected Label getLabel(String id, String titleText, String styleClass, boolean isVisible, double prefWidth) {
		/** Field Title */
		Label label = new Label();
		label.setId(id);
		label.setText(titleText);
		label.getStyleClass().add(styleClass);
		label.setVisible(isVisible);
		label.setWrapText(true);
		// label.setPrefWidth(prefWidth);
		return label;
	}

	protected RegistrationDTO getRegistrationDTo() {
		RegistrationDTO registrationDTO = null;
		if (SessionContext.map() != null || !SessionContext.map().isEmpty()) {
			registrationDTO = (RegistrationDTO) SessionContext.map().get(RegistrationConstants.REGISTRATION_DATA);
		}
		return registrationDTO;
	}

	protected boolean isFieldVisible(UiFieldDTO schemaDTO) {
		if (requiredFieldValidator == null) {
			requiredFieldValidator = ClientApplication.getApplicationContext().getBean(RequiredFieldValidator.class);
		}
		try {
			boolean isVisibleAccordingToSpec = requiredFieldValidator.isFieldVisible(schemaDTO, getRegistrationDTo());

			switch (getRegistrationDTo().getFlowType()) {
				case UPDATE:
					return (getRegistrationDTo().getUpdatableFields().contains(schemaDTO.getId())) ? isVisibleAccordingToSpec : false;
				case CORRECTION:
				case NEW:
				case LOST:
					return isVisibleAccordingToSpec;
			}
		} catch (Exception exception) {
			LOGGER.error("Failed to check field visibility", exception);
		}
		return true;
	}

	protected void changeNodeOrientation(Node node, String langCode) {

		if (ApplicationContext.getInstance().isLanguageRightToLeft(langCode)) {
			node.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
		}
	}

	protected void addValidationMessage(VBox vBox, String id, String langCode, String styleClass, boolean isVisible) {
		Label validationMessage = getLabel(id + langCode + RegistrationConstants.MESSAGE, null,
				styleClass, isVisible, 0);
		validationMessage.setWrapText(false);
		vBox.getChildren().add(validationMessage);

		HBox validationHBox = new HBox();
		validationHBox.setSpacing(20);
		validationHBox.getChildren().add(validationMessage);
		validationHBox.setStyle("-fx-background-color:WHITE");
		vBox.getChildren().add(validationHBox);
	}

	protected Node getField(String id) {
		return node.lookup(RegistrationConstants.HASH + id);
	}

	protected FxControl getFxControl(String fieldId) {
		return GenericController.getFxControlMap().get(fieldId);
	}

	public UiFieldDTO getUiSchemaDTO() {
		return uiFieldDTO;
	}

	public Node getNode() {
		return this.node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	private static final String RED_ASTERISK_FLAG = "redAsteriskInstalled";

	/**
	 * Walks the node tree and, for every Labeled whose text ends with the mandatory
	 * suffix, removes the plain "*" from the text and shows it as a red graphic instead.
	 * Keeps working when the text is updated later (listener based).
	 */
	public static void applyRedAsterisk(Node root) {
		if (root == null) return;
		if (root instanceof javafx.scene.control.Labeled
				&& !(root instanceof javafx.scene.control.Button)) {
			installRedAsterisk((javafx.scene.control.Labeled) root);
		}
		if (root instanceof javafx.scene.Parent) {
			for (Node child : ((javafx.scene.Parent) root).getChildrenUnmodifiable()) {
				applyRedAsterisk(child);
			}
		}
	}

	private static void installRedAsterisk(javafx.scene.control.Labeled labeled) {
		if (labeled.getProperties().containsKey(RED_ASTERISK_FLAG)) return;
		labeled.getProperties().put(RED_ASTERISK_FLAG, Boolean.TRUE);
		updateRedAsterisk(labeled, labeled.getText());
		labeled.textProperty().addListener((obs, oldText, newText) -> updateRedAsterisk(labeled, newText));
	}

	private static void updateRedAsterisk(javafx.scene.control.Labeled labeled, String text) {
		if (text == null) return;
		String trimmed = text.replaceAll("\\s+$", "");
		if (trimmed.endsWith("*")) {
			String clean = trimmed.substring(0, trimmed.length() - 1).replaceAll("\\s+$", "");
			if (labeled instanceof javafx.scene.control.CheckBox) {
				javafx.scene.control.CheckBox cb = (javafx.scene.control.CheckBox) labeled;
				javafx.scene.text.Text t1 = new javafx.scene.text.Text(clean);
				t1.setStyle("-fx-fill: #1E293B; -fx-font-family: 'Segoe UI', -apple-system, Roboto, sans-serif; -fx-font-size: 13.5px; -fx-font-weight: 500;");
				javafx.scene.text.Text tStar = new javafx.scene.text.Text(" *");
				tStar.setStyle("-fx-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 14px;");
				javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow(t1, tStar);
				flow.setLineSpacing(4);
				flow.setPadding(new javafx.geometry.Insets(1, 0, 0, 12));
				double w = cb.getPrefWidth() > 0 ? cb.getPrefWidth() - 40 : 750;
				flow.setMaxWidth(w);
				flow.setPrefWidth(w);
				cb.setGraphic(flow);
				cb.setGraphicTextGap(12);
				cb.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
				cb.setText("");
			} else {
				javafx.scene.text.Text star = new javafx.scene.text.Text("*");
				star.setStyle("-fx-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 14px;");
				labeled.setGraphic(star);
				labeled.setGraphicTextGap(4);
				labeled.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
				labeled.setText(clean); // re-triggers listener, but no trailing "*" so no-op
			}
		}
	}

}
