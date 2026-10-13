package io.mosip.registration.controller.reg;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.ResourceBundle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.dto.RegistrationDTO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Class for showing the Acknowledgement Receipt
 *
 * @author Himaja Dhanyamraju
 *
 */
@Controller
public class AckReceiptController extends BaseController implements Initializable {

	private static final Logger LOGGER = AppConfig.getLogger(AckReceiptController.class);

	@Autowired
	private PacketHandlerController packetController;

	private Writer stringWriter;

	@FXML
	protected GridPane rootPane;

	@FXML
	private WebView webView;

	@FXML
	private Button newRegistration;

	@FXML
	private Button print;

	// @FXML
	// private Button sendNotification;

	@FXML
	private ImageView newRegistrationBtnImgVw;
	@FXML
	private ImageView printImgVw;	
	@FXML
	private ImageView SendEmailImageView;
;

	@Autowired
	private SendNotificationController sendNotificationController;

	public void setStringWriter(Writer stringWriter) {
		this.stringWriter = stringWriter;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
				"Page loading has been started");
		setImage(newRegistrationBtnImgVw, RegistrationConstants.NEW_REGISTRATION_IMG);
		setImage(printImgVw, RegistrationConstants.PRINTER_IMG);
		setImage(SendEmailImageView, RegistrationConstants.SEND_EMAIL_IMG);
		
		// setImagesOnHover();
		String notificationType = getValueFromApplicationContext(RegistrationConstants.MODE_OF_COMMUNICATION);
		/*
		 * if (notificationType != null && !notificationType.trim().isEmpty() &&
		 * !notificationType.equals("NONE")) {
		 *
		 * sendNotification.setVisible(false); } else {
		 * sendNotification.setVisible(false); }
		 */

		WebEngine engine = webView.getEngine();
		// loads the generated HTML template content into webview
		engine.loadContent(stringWriter.toString());
		LOGGER.info("REGISTRATION - UI - ACK-RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
				"Acknowledgement template has been loaded to webview");
	}

	/*
	 * private void setImagesOnHover() { Image sendEmailInWhite = new Image(
	 * getClass().getResourceAsStream(RegistrationConstants.
	 * SEND_EMAIL_FOCUSED_IMAGE_PATH)); Image sendEmailImage = new
	 * Image(getClass().getResourceAsStream(RegistrationConstants.
	 * SEND_EMAIL_IMAGE_PATH));
	 *
	 * sendNotification.hoverProperty().addListener((ov, oldValue, newValue) -> { if
	 * (newValue) { sendNotificationImageView.setImage(sendEmailInWhite); } else {
	 * sendNotificationImageView.setImage(sendEmailImage); } }); }
	 */

	private Stage printModalStage;

	/**
	 * To display a modern print confirmation and thermal receipt preview modal
	 * before sending the slip to the BioRugged 80mm direct thermal printer.
	 *
	 * @param event - the event that happens on click of print button
	 */
	@FXML
	public void printReceipt(ActionEvent event) {
		LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Opening modern print confirmation modal");
		showModernPrintModal();
	}

	private void showModernPrintModal() {
		try {
			if (printModalStage != null && printModalStage.isShowing()) {
				printModalStage.close();
			}

			String htmlContent = (stringWriter != null) ? stringWriter.toString() : "";
			Map<String, String> htmlFields = extractFieldsFromHtml(htmlContent);

			RegistrationDTO regDto = getRegistrationDTOFromSession();
			String regId = (regDto != null && regDto.getRegistrationId() != null) ? regDto.getRegistrationId() : null;
			if (regId == null || regId.trim().isEmpty() || regId.equals("N/A")) {
				regId = extractByPattern(htmlContent, "(?:id-highlight|th-rid-val)[\"'][^>]*>\\s*([0-9a-zA-Z]+)");
			}
			if (regId == null || regId.trim().isEmpty()) {
				regId = "N/A";
			}

			// Name extraction: First Name + Surname or Full Name from rendered HTML (or regDto fallback)
			String nameVal = extractFieldByKeywords(htmlFields, "th:name / nome:");
			if (nameVal == null || nameVal.trim().isEmpty() || nameVal.equalsIgnoreCase("Applicant")) {
				String fn = extractFieldByKeywords(htmlFields, "primeiro nome", "first name");
				String sn = extractFieldByKeywords(htmlFields, "sobrenome", "surname");
				if (fn != null && sn != null) {
					nameVal = fn + " " + sn;
				} else if (fn != null) {
					nameVal = fn;
				} else if (sn != null) {
					nameVal = sn;
				} else {
					nameVal = extractFieldByKeywords(htmlFields, "nome completo", "full name");
				}
			}
			if (nameVal == null || nameVal.trim().isEmpty() || nameVal.equalsIgnoreCase("Applicant")) {
				nameVal = getDemographicValue(regDto, "fullName", "firstName", "surname", "name");
			}
			if (nameVal == null || nameVal.trim().isEmpty()) {
				nameVal = "Applicant";
			}

			// DOB
			String dobVal = extractFieldByKeywords(htmlFields, "th:dob / data nasc.:", "data de nascimento", "date of birth", "dob", "data nasc");
			if (dobVal == null || dobVal.trim().isEmpty() || dobVal.equalsIgnoreCase("N/A")) {
				dobVal = getDemographicValue(regDto, "dateOfBirth", "dob");
			}
			if (dobVal == null || dobVal.trim().isEmpty()) {
				dobVal = "N/A";
			}

			// Gender
			String genderVal = extractFieldByKeywords(htmlFields, "th:gender / gênero:", "gênero", "genero", "sexo", "gender");
			if (genderVal == null || genderVal.trim().isEmpty() || genderVal.equalsIgnoreCase("N/A")) {
				genderVal = getDemographicValue(regDto, "gender");
			}
			if (genderVal == null || genderVal.trim().isEmpty()) {
				genderVal = "N/A";
			}

			// Marital Status
			String maritalVal = extractFieldByKeywords(htmlFields, "th:marital / est. civil:", "estado civil", "marital status", "marital");
			if (maritalVal == null || maritalVal.trim().isEmpty() || maritalVal.equalsIgnoreCase("N/A")) {
				maritalVal = getDemographicValue(regDto, "maritalStatus", "marital_status");
			}
			if (maritalVal == null || maritalVal.trim().isEmpty()) {
				maritalVal = "N/A";
			}

			// Mobile
			String mobileVal = extractFieldByKeywords(htmlFields, "th:mobile / celular:", "número de celular", "numero de celular", "mobile number", "mobile", "celular", "phone");
			if (mobileVal == null || mobileVal.trim().isEmpty() || mobileVal.equalsIgnoreCase("N/A")) {
				mobileVal = getDemographicValue(regDto, "mobileNumber", "phone", "mobile");
			}
			if (mobileVal == null || mobileVal.trim().isEmpty()) {
				mobileVal = "N/A";
			}

			// Address
			String addressVal = extractFieldByKeywords(htmlFields, "th:address / endereço:");
			if (addressVal == null || addressVal.trim().isEmpty() || addressVal.equalsIgnoreCase("N/A")) {
				List<String> addrParts = new ArrayList<>();
				String a1 = extractFieldByKeywords(htmlFields, "endereço, linha 1", "endereco, linha 1", "address line 1");
				if (a1 != null && !a1.equalsIgnoreCase("N/A")) addrParts.add(a1);
				String a2 = extractFieldByKeywords(htmlFields, "endereço, linha 2", "endereco, linha 2", "address line 2");
				if (a2 != null && !a2.equalsIgnoreCase("N/A")) addrParts.add(a2);
				String isl = extractFieldByKeywords(htmlFields, "ilha", "island");
				if (isl != null && !isl.equalsIgnoreCase("N/A") && !addrParts.contains(isl)) addrParts.add(isl);
				String dst = extractFieldByKeywords(htmlFields, "distrito", "district");
				if (dst != null && !dst.equalsIgnoreCase("N/A") && !addrParts.contains(dst)) addrParts.add(dst);
				String mun = extractFieldByKeywords(htmlFields, "município", "municipio", "municipality");
				if (mun != null && !mun.equalsIgnoreCase("N/A") && !addrParts.contains(mun)) addrParts.add(mun);
				String twn = extractFieldByKeywords(htmlFields, "cidade", "town", "city");
				if (twn != null && !twn.equalsIgnoreCase("N/A") && !addrParts.contains(twn)) addrParts.add(twn);

				if (!addrParts.isEmpty()) {
					addressVal = String.join(", ", addrParts);
				}
			}
			if (addressVal == null || addressVal.trim().isEmpty() || addressVal.equalsIgnoreCase("N/A")) {
				String addr1 = getDemographicValue(regDto, "addressLine1", "address");
				String island = getDemographicValue(regDto, "island", "city", "town");
				addressVal = "";
				if (addr1 != null) addressVal += addr1;
				if (island != null) addressVal += (addressVal.isEmpty() ? "" : ", ") + island;
			}
			if (addressVal == null || addressVal.trim().isEmpty()) {
				addressVal = "N/A";
			}

			String docVal = "POI_PAS / POI_CED";
			try {
				if (regDto != null && regDto.getDocuments() != null && !regDto.getDocuments().isEmpty()) {
					for (Map.Entry<String, io.mosip.registration.dto.packetmanager.DocumentDto> entry : regDto.getDocuments().entrySet()) {
						if (entry.getValue() != null && entry.getValue().getValue() != null && !entry.getValue().getValue().isEmpty()) {
							docVal = entry.getValue().getValue();
							break;
						} else if (entry.getKey() != null) {
							docVal = entry.getKey();
							break;
						}
					}
				}
			} catch (Exception ignored) {
			}

			String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"));

			String centerName = "Innovation and Knowledge Institute";
			String officerId = "rohail";
			try {
				if (SessionContext.userContext() != null) {
					if (SessionContext.userContext().getRegistrationCenterDetailDTO() != null
							&& SessionContext.userContext().getRegistrationCenterDetailDTO().getRegistrationCenterName() != null) {
						centerName = SessionContext.userContext().getRegistrationCenterDetailDTO().getRegistrationCenterName();
					}
					if (SessionContext.userContext().getUserId() != null) {
						officerId = SessionContext.userContext().getUserId();
					}
				}
			} catch (Exception ignored) {
			}

			// Attempt to extract real QR code image from the loaded HTML
			Image qrImage = null;
			try {
				if (stringWriter != null) {
					String html = stringWriter.toString();
					int qrIdx = html.indexOf("data:image/png;base64,");
					if (qrIdx != -1) {
						int endIdx = html.indexOf("\"", qrIdx);
						if (endIdx != -1) {
							String base64Data = html.substring(qrIdx + "data:image/png;base64,".length(), endIdx);
							byte[] bytes = java.util.Base64.getDecoder().decode(base64Data);
							qrImage = new Image(new java.io.ByteArrayInputStream(bytes));
						}
					}
				}
			} catch (Exception ignored) {
			}

			Stage stage = new Stage(StageStyle.TRANSPARENT);

			// Printer Icon Badge
			Label iconBadge = new Label("🖨");
			iconBadge.setMinSize(42, 42);
			iconBadge.setPrefSize(42, 42);
			iconBadge.setAlignment(Pos.CENTER);
			iconBadge.setStyle("-fx-background-color: #ECFDF5; -fx-background-radius: 21; -fx-border-color: #A7F3D0;"
					+ "-fx-border-radius: 21; -fx-border-width: 1.5; -fx-text-fill: #065F46; -fx-font-size: 19px;");

			// Header Title (subtitle removed as per user request)
			Label titleLabel = new Label("Print Acknowledgement Slip");
			titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

			VBox headerText = new VBox(titleLabel);
			headerText.setAlignment(Pos.CENTER_LEFT);

			Region headerSpacer = new Region();
			HBox.setHgrow(headerSpacer, Priority.ALWAYS);

			Button closeBtn = new Button("✕");
			closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 3 8;");
			closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 6; -fx-text-fill: #0F172A; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 3 8;"));
			closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 3 8;"));
			closeBtn.setOnAction(e -> stage.close());

			HBox headerBox = new HBox(12, iconBadge, headerText, headerSpacer, closeBtn);
			headerBox.setAlignment(Pos.CENTER_LEFT);

			// ==========================================
			// GLOSSY THERMAL PAPER ROLL (80MM)
			// ==========================================
			VBox paperSlip = new VBox(6);
			paperSlip.setMaxWidth(300);
			paperSlip.setPrefWidth(300);
			paperSlip.setAlignment(Pos.TOP_CENTER);
			// Cylindrical glossy paper sheen gradient + drop shadow
			paperSlip.setStyle("-fx-background-color: linear-gradient(to right, #F3F5F8 0%, #FFFFFF 12%, #FFFFFF 88%, #EDF1F5 100%); "
					+ "-fx-border-color: #CBD5E1; -fx-border-width: 1px; "
					+ "-fx-padding: 14 14 18 14; -fx-effect: dropshadow(gaussian, rgba(15, 23, 42, 0.22), 16.0, 0.0, 0.0, 6.0);");

			// Top Perforated Edge
			Label tearTop = new Label("- - - - - - - - - - - - - - - - - - - - - - - - -");
			tearTop.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-text-fill: #94A3B8; -fx-font-size: 9.5px;");
			tearTop.setAlignment(Pos.CENTER);
			paperSlip.getChildren().add(tearTop);

			// 1. Logo
			try {
				java.io.InputStream is = getClass().getResourceAsStream("/images/logo-final-small.png");
				if (is != null) {
					Image logoImg = new Image(is);
					ImageView logoView = new ImageView(logoImg);
					logoView.setFitHeight(44);
					logoView.setFitWidth(44);
					logoView.setPreserveRatio(true);
					logoView.setSmooth(true);
					paperSlip.getChildren().add(logoView);
				}
			} catch (Exception ignored) {
			}

			// 2. Title: Registration acknowledgement
			Label docTitle = new Label("Registration acknowledgement");
			docTitle.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #000000; -fx-alignment: center; -fx-padding: 2 0 1 0;");
			paperSlip.getChildren().addAll(docTitle, createReceiptDivider());

			// 3. QR Code + "scan to verify"
			if (qrImage != null) {
				ImageView qrView = new ImageView(qrImage);
				qrView.setFitWidth(100);
				qrView.setFitHeight(100);
				qrView.setPreserveRatio(true);
				qrView.setSmooth(false);
				Label qrCaption = new Label("scan to verify");
				qrCaption.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-text-fill: #000000; -fx-alignment: center; -fx-padding: 2 0 0 0;");
				paperSlip.getChildren().addAll(qrView, qrCaption, createReceiptDivider());
			}

			// 4. Box: APPLICATION ID / RID
			VBox ridBox = new VBox(3);
			ridBox.setAlignment(Pos.CENTER);
			ridBox.setStyle("-fx-border-color: #000000; -fx-border-width: 1.5px; -fx-padding: 6 8; -fx-background-color: #FFFFFF;");

			Label ridTitle = new Label("APPLICATION ID / RID");
			ridTitle.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 9px; -fx-font-weight: 800; -fx-text-fill: #000000; -fx-letter-spacing: 0.5px;");

			Label ridNum = new Label(regId);
			ridNum.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12.5px; -fx-font-weight: 800; -fx-text-fill: #000000;");

			Label dateRow = new Label("Date / Data: " + formattedDate);
			dateRow.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #000000;");

			ridBox.getChildren().addAll(ridTitle, ridNum, dateRow);
			paperSlip.getChildren().addAll(ridBox, createReceiptDivider());

			// 5. APPLICANT / CANDIDATO Section
			paperSlip.getChildren().add(createReceiptSectionHeader("APPLICANT / CANDIDATO"));
			paperSlip.getChildren().add(createReceiptRow("Name / Nome:", nameVal));
			paperSlip.getChildren().add(createReceiptRow("DOB / Data nasc.:", dobVal));
			paperSlip.getChildren().add(createReceiptRow("Gender / Gênero:", genderVal));
			paperSlip.getChildren().add(createReceiptRow("Marital / Est. Civil:", maritalVal));
			paperSlip.getChildren().add(createReceiptRow("Mobile / Celular:", mobileVal));
			paperSlip.getChildren().add(createReceiptRow("Address / Endereço:", addressVal));
			paperSlip.getChildren().add(createReceiptDivider());

			// 6. CENTRO & AGENTE Section
			paperSlip.getChildren().add(createReceiptSectionHeader("CENTRO & AGENTE"));
			paperSlip.getChildren().add(createReceiptRow("Center / Centro:", centerName));
			paperSlip.getChildren().add(createReceiptRow("Officer / Agente:", officerId));
			paperSlip.getChildren().add(createReceiptDivider());

			// 7. Retention Notices & End of Receipt
			Label notice1 = new Label("Please retain this slip for tracking application status.");
			notice1.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #000000; -fx-alignment: center; -fx-text-alignment: center;");
			notice1.setWrapText(true);

			Label notice2 = new Label("Guarde este recibo para acompanhar seu pedido.");
			notice2.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 8.5px; -fx-font-weight: bold; -fx-text-fill: #000000; -fx-alignment: center; -fx-text-alignment: center;");
			notice2.setWrapText(true);

			Label endSlip = new Label("*** END OF RECEIPT / FIM DO RECIBO ***");
			endSlip.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 9.5px; -fx-font-weight: 800; -fx-text-fill: #000000; -fx-alignment: center; -fx-padding: 3 0;");

			Label tearBottom = new Label("- - - - - - - - - - - - ✂ - - - - - - - - - - - -");
			tearBottom.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-text-fill: #64748B; -fx-font-size: 10px;");
			tearBottom.setAlignment(Pos.CENTER);

			paperSlip.getChildren().addAll(notice1, notice2, endSlip, tearBottom);

			// Paper Roll Scroll Container inside Workbench
			ScrollPane paperScroll = new ScrollPane(paperSlip);
			paperScroll.setFitToWidth(true);
			paperScroll.setPrefViewportHeight(380);
			paperScroll.setMaxHeight(400);
			paperScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");

			VBox previewWorkbench = new VBox(paperScroll);
			previewWorkbench.setAlignment(Pos.TOP_CENTER);
			previewWorkbench.setStyle("-fx-background-color: #E2E8F0; -fx-background-radius: 12; -fx-border-color: #CBD5E1; -fx-border-radius: 12; -fx-padding: 12 14;");

			// Action Buttons
			Button cancelBtn = new Button("Cancel");
			cancelBtn.setPrefHeight(36);
			cancelBtn.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8; -fx-text-fill: #475569;"
					+ "-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 18;");
			cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle("-fx-background-color: #E2E8F0; -fx-background-radius: 8; -fx-text-fill: #1E293B;"
					+ "-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 18;"));
			cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8; -fx-text-fill: #475569;"
					+ "-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 18;"));
			cancelBtn.setOnAction(e -> stage.close());

			Button printBtn = new Button("🖨  Print Slip");
			printBtn.setDefaultButton(true);
			printBtn.setPrefHeight(36);
			String printBaseStyle = "-fx-background-radius: 8; -fx-text-fill: white; -fx-font-size: 12.5px;"
					+ "-fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 24;";
			printBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #15803D, #116530);" + printBaseStyle
					+ "-fx-effect: dropshadow(gaussian, rgba(21,128,61,0.28), 8, 0, 0, 2);");
			printBtn.setOnMouseEntered(e -> printBtn.setStyle("-fx-background-color: #0a391b;" + printBaseStyle
					+ "-fx-effect: dropshadow(gaussian, rgba(21,128,61,0.40), 10, 0, 0, 3);"));
			printBtn.setOnMouseExited(e -> printBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #15803D, #116530);" + printBaseStyle
					+ "-fx-effect: dropshadow(gaussian, rgba(21,128,61,0.28), 8, 0, 0, 2);"));
			printBtn.setOnAction(e -> {
				stage.close();
				executeThermalPrint();
			});

			Region btnSpacer = new Region();
			HBox.setHgrow(btnSpacer, Priority.ALWAYS);

			HBox buttonBox = new HBox(10, btnSpacer, cancelBtn, printBtn);
			buttonBox.setAlignment(Pos.CENTER_RIGHT);

			// Main Modal Card (specStrip removed as per user request)
			VBox card = new VBox(14, headerBox, previewWorkbench, buttonBox);
			card.setMaxWidth(440);
			card.setPrefWidth(440);
			card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-padding: 20 22 18 22;"
					+ "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.30), 30, 0, 0, 8);");

			StackPane root = new StackPane(card);
			root.setStyle("-fx-background-color: rgba(15, 23, 42, 0.45); -fx-padding: 20;");
			root.setOnMouseClicked(e -> {
				if (e.getTarget() == root) {
					stage.close();
				}
			});

			Scene scene = new Scene(root);
			scene.setFill(Color.TRANSPARENT);
			scene.setOnKeyPressed(e -> {
				if (e.getCode() == KeyCode.ESCAPE) {
					stage.close();
				}
			});

			Window owner = (rootPane != null && rootPane.getScene() != null) ? rootPane.getScene().getWindow()
					: (webView != null && webView.getScene() != null ? webView.getScene().getWindow() : null);

			if (owner != null) {
				stage.initOwner(owner);
				stage.initModality(Modality.WINDOW_MODAL);
				stage.setOnShown(e -> {
					stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
					stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
				});
			}

			stage.setScene(scene);

			// Entrance animation
			card.setOpacity(0);
			card.setScaleX(0.92);
			card.setScaleY(0.92);

			FadeTransition fade = new FadeTransition(Duration.millis(180), card);
			fade.setToValue(1);

			ScaleTransition scale = new ScaleTransition(Duration.millis(180), card);
			scale.setToX(1);
			scale.setToY(1);

			printModalStage = stage;
			stage.show();
			new ParallelTransition(fade, scale).play();

		} catch (Exception e) {
			LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Error showing modern print modal: " + e.getMessage());
			// Fallback to direct print if modal fails
			executeThermalPrint();
		}
	}

	private HBox createReceiptRow(String label, String value) {
		HBox row = new HBox(4);
		row.setAlignment(Pos.CENTER_LEFT);
		Label lbl = new Label(label);
		lbl.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-text-fill: #000000;");
		lbl.setMinWidth(Region.USE_PREF_SIZE);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		Label val = new Label(value);
		val.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-text-fill: #000000; -fx-alignment: center-right;");
		val.setWrapText(true);
		val.setMaxWidth(160);

		row.getChildren().addAll(lbl, spacer, val);
		return row;
	}

	private Label createReceiptDivider() {
		Label div = new Label("- - - - - - - - - - - - - - - - - - - - - - - - -");
		div.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-text-fill: #000000; -fx-font-size: 9.5px;");
		div.setAlignment(Pos.CENTER);
		div.setMaxWidth(Double.MAX_VALUE);
		return div;
	}

	private Label createReceiptSectionHeader(String title) {
		Label h = new Label(title);
		h.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 10.5px; -fx-font-weight: 800; -fx-text-fill: #000000; -fx-padding: 3 0 1 0;");
		return h;
	}

	private String getDemographicValue(RegistrationDTO regDto, String... keys) {
		if (regDto == null) {
			return null;
		}
		for (String key : keys) {
			if (regDto.getDemographics() != null && regDto.getDemographics().containsKey(key)) {
				String val = extractObjValue(regDto.getDemographics().get(key));
				if (val != null && !val.isEmpty()) {
					return val;
				}
			}
			if (regDto.getDefaultDemographics() != null && regDto.getDefaultDemographics().containsKey(key)) {
				String val = extractObjValue(regDto.getDefaultDemographics().get(key));
				if (val != null && !val.isEmpty()) {
					return val;
				}
			}
		}
		return null;
	}

	private String extractObjValue(Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof List<?>) {
			List<?> list = (List<?>) obj;
			List<String> values = new ArrayList<>();
			for (Object item : list) {
				if (item instanceof io.mosip.commons.packet.dto.packet.SimpleDto) {
					String v = ((io.mosip.commons.packet.dto.packet.SimpleDto) item).getValue();
					if (v != null && !v.trim().isEmpty()) {
						values.add(v.trim());
					}
				} else if (item instanceof Map) {
					Object v = ((Map<?, ?>) item).get("value");
					if (v != null && !v.toString().trim().isEmpty()) {
						values.add(v.toString().trim());
					}
				} else if (item != null) {
					values.add(item.toString().trim());
				}
			}
			if (!values.isEmpty()) {
				return String.join(" / ", values);
			}
			return null;
		}
		if (obj instanceof Map) {
			Object v = ((Map<?, ?>) obj).get("value");
			return v != null ? v.toString().trim() : obj.toString().trim();
		}
		return obj.toString().trim();
	}

	private Map<String, String> extractFieldsFromHtml(String html) {
		Map<String, String> fields = new LinkedHashMap<>();
		if (html == null || html.isEmpty()) {
			return fields;
		}

		// 1. Matches <div class="field-label">Label</div>\s*<div class="field-value">Value</div>
		Pattern p1 = Pattern.compile("<div[^>]*class=[\"']field-label[\"'][^>]*>(.*?)</div>\\s*<div[^>]*class=[\"']field-value[\"'][^>]*>(.*?)</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher m1 = p1.matcher(html);
		while (m1.find()) {
			String label = cleanHtmlText(m1.group(1));
			String val = cleanHtmlText(m1.group(2));
			if (!label.isEmpty() && !val.isEmpty()) {
				fields.put(label.toLowerCase(), val);
			}
		}

		// 2. Matches <span class="th-row-label">Label:</span>\s*<span class="th-row-val[^>]*>Value</span>
		Pattern p2 = Pattern.compile("<span[^>]*class=[\"']th-row-label[\"'][^>]*>(.*?)</span>\\s*<span[^>]*class=[\"']th-row-val[^\"']*[\"'][^>]*>(.*?)</span>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher m2 = p2.matcher(html);
		while (m2.find()) {
			String label = cleanHtmlText(m2.group(1));
			String val = cleanHtmlText(m2.group(2));
			if (!label.isEmpty() && !val.isEmpty()) {
				fields.put("th:" + label.toLowerCase(), val);
			}
		}

		// 3. Fallback: matches <span class="meta-label">Label</span>\s*<span class="meta-value">Value</span>
		Pattern p3 = Pattern.compile("<span[^>]*class=[\"']meta-label[\"'][^>]*>(.*?)</span>\\s*<span[^>]*class=[\"']meta-value[^\"']*[\"'][^>]*>(.*?)</span>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher m3 = p3.matcher(html);
		while (m3.find()) {
			String label = cleanHtmlText(m3.group(1));
			String val = cleanHtmlText(m3.group(2));
			if (!label.isEmpty() && !val.isEmpty()) {
				fields.put(label.toLowerCase(), val);
			}
		}

		return fields;
	}

	private String extractFieldByKeywords(Map<String, String> fields, String... keywords) {
		for (Map.Entry<String, String> entry : fields.entrySet()) {
			String label = entry.getKey();
			for (String kw : keywords) {
				if (label.contains(kw.toLowerCase())) {
					String val = entry.getValue();
					if (val != null && !val.trim().isEmpty() && !val.trim().equalsIgnoreCase("N/A")) {
						return val.trim();
					}
				}
			}
		}
		return null;
	}

	private String extractByPattern(String text, String regex) {
		if (text == null) return null;
		Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher m = p.matcher(text);
		if (m.find()) {
			return cleanHtmlText(m.group(1));
		}
		return null;
	}

	private String cleanHtmlText(String text) {
		if (text == null) return "";
		String clean = text.replaceAll("<[^>]*>", " ");
		clean = clean.replace("&nbsp;", " ")
				.replace("&amp;", "&")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&#39;", "'");
		return clean.replaceAll("\\s+", " ").trim();
	}

	private void executeThermalPrint() {
		try {
			LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Printing Acknowledgement Receipt to 80mm Thermal Printer");

			javafx.print.Printer targetPrinter = null;
			for (javafx.print.Printer p : javafx.print.Printer.getAllPrinters()) {
				if (p.getName().toLowerCase().contains("pos") || p.getName().toLowerCase().contains("80") || p.getName().toLowerCase().contains("thermal")) {
					targetPrinter = p;
					break;
				}
			}
			if (targetPrinter == null) {
				targetPrinter = javafx.print.Printer.getDefaultPrinter();
			}

			PrinterJob job = (targetPrinter != null) ? PrinterJob.createPrinterJob(targetPrinter) : PrinterJob.createPrinterJob();
			if (job != null) {
				RegistrationDTO regDto = getRegistrationDTOFromSession();
				String regId = (regDto != null && regDto.getRegistrationId() != null) ? regDto.getRegistrationId() : "Ack";
				job.getJobSettings().setJobName(regId + "_Thermal_80mm");

				javafx.print.Printer printer = job.getPrinter();
				if (printer != null) {
					javafx.print.PageLayout pageLayout = printer.createPageLayout(
							printer.getDefaultPageLayout().getPaper(),
							javafx.print.PageOrientation.PORTRAIT,
							javafx.print.Printer.MarginType.HARDWARE_MINIMUM);
					job.getJobSettings().setPageLayout(pageLayout);
				}

				webView.getEngine().print(job);
				job.endJob();
				generateAlert(RegistrationConstants.ALERT_INFORMATION,
						RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PRINT_INITIATION_SUCCESS));
			} else {
				LOGGER.warn("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, "No PrinterJob available");
				generateAlert(RegistrationConstants.ERROR, "Unable to find thermal printer. Please verify device connection.");
			}
		} catch (Exception e) {
			LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Error while printing to thermal printer: " + e.getMessage());
			generateAlert(RegistrationConstants.ERROR, "Printing error: " + e.getMessage());
		}
	}

	@FXML
	public void sendNotification(ActionEvent event) {
		LOGGER.debug("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Going to Send Notification Popup Window");

		sendNotificationController.init();
	}

	@FXML
	public void goToNewRegistration(ActionEvent event) {
		LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Going to New Registration Page after packet creation");

		clearRegistrationData();
		goToHomePageFromRegistration();
	}

	/**
	 * Go to home ack template.
	 */
	public void goToHomeAckTemplate() {
		try {
			BaseController.load(getClass().getResource(RegistrationConstants.HOME_PAGE));
			if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
				clearOnboardData();
				clearRegistrationData();
			} else {
				SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
						RegistrationConstants.ENABLE);
			}
		} catch (IOException ioException) {
			LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		} catch (RuntimeException runtimException) {
			LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					runtimException.getMessage() + ExceptionUtils.getStackTrace(runtimException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		}

	}

}