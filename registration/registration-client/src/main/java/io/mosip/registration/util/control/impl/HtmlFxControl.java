package io.mosip.registration.util.control.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.controller.ClientApplication;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;


import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.templatemanager.spi.TemplateManager;
import io.mosip.kernel.core.templatemanager.spi.TemplateManagerBuilder;
import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.Initialization;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.service.template.TemplateService;
import io.mosip.registration.util.control.FxControl;
import javafx.scene.Node;
import javafx.scene.web.WebView;

/**
 * Builds HTML content based on the provided templateName in the first language
 * select in the order of selection.
 *
 */
public class HtmlFxControl extends FxControl {

    private static final Logger LOGGER = AppConfig.getLogger(HtmlFxControl.class);
    private static final String DEFAULT_TEMPLATE = "<!DOCTYPE html><html><body><h1>Template Name not set !!</h1></body></html>";
    private TemplateManagerBuilder templateManagerBuilder;
    private TemplateService templateService;
    private Map<String, String> contentHash = null;

    public HtmlFxControl() {
        org.springframework.context.ApplicationContext applicationContext = ClientApplication.getApplicationContext();
        auditFactory = applicationContext.getBean(AuditManagerService.class);
        templateManagerBuilder = applicationContext.getBean(TemplateManagerBuilder.class);
        templateService = applicationContext.getBean(TemplateService.class);
        contentHash = new HashMap<>();
    }

    @Override
    public FxControl build(UiFieldDTO uiFieldDTO) {
        this.uiFieldDTO = uiFieldDTO;
        this.control = this;

        final VBox vBox = new VBox();
        vBox.setId(this.uiFieldDTO.getId());
        vBox.setSpacing(2);

        List<String> labels = new ArrayList<>();
        getRegistrationDTo().getSelectedLanguagesByApplicant().forEach(langCode -> {
            String text = this.uiFieldDTO.getLabel().get(langCode);
            if (text != null && !text.trim().isEmpty()) {
                labels.add(text);
            }
        });

        if (!labels.isEmpty()) {
            Label label = new Label();
            label.setText(String.join(RegistrationConstants.SLASH, labels));
            label.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
            vBox.getChildren().add(label);
        }

        final Accordion accordion = new Accordion();
        ResourceBundle resourceBundle = ResourceBundle.getBundle(RegistrationConstants.LABELS, Locale.getDefault());
        getRegistrationDTo().getSelectedLanguagesByApplicant().forEach(langCode -> {
            final TitledPane titledPane = new TitledPane(resourceBundle.getString(langCode), null);
            titledPane.setContent(buildWebView(langCode, titledPane));
            accordion.getPanes().add(titledPane);
            changeNodeOrientation(titledPane, langCode);
            titledPane.setId(uiFieldDTO.getId()+langCode);
        });

        //accordion.setExpandedPane(accordion.getPanes().get(0));
        vBox.getChildren().add(accordion);

        this.node = vBox;

        auditFactory.audit(AuditEvent.REG_HTML_FX_CONTROL, Components.REG_DEMO_DETAILS, SessionContext.userId(),
                AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

        return this.control;
    }

    @Override
    public void setData(Object data) {
        if (this.uiFieldDTO.getType().equalsIgnoreCase(RegistrationConstants.SIMPLE_TYPE)) {
            List<SimpleDto> values = new ArrayList<SimpleDto>();
            for (String langCode : getRegistrationDTo().getSelectedLanguagesByApplicant()) {
               values.add(new SimpleDto(langCode, contentHash.get(langCode)));
            }
            getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), values);

        } else {
            getRegistrationDTo().addDemographicField(uiFieldDTO.getId(),
                    contentHash.get(getRegistrationDTo().getSelectedLanguagesByApplicant().get(0)));
        }
    }

    @Override
    public void fillData(Object data) {
    }

    @Override
    public Object getData() {
        return getRegistrationDTo().getDemographics().get(uiFieldDTO.getId());
    }

    @Override
    public boolean isValid() {
        return !contentHash.isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return contentHash.isEmpty();
    }

    @Override
    public List<GenericDto> getPossibleValues(String langCode) {
        return null;
    }

    @Override
    public void setListener(Node node) {
    }

    @Override
    public void selectAndSet(Object data) {
    }

    private VBox buildWebView(String langCode, TitledPane titledPane) {
    	VBox vbox = new VBox();

        final WebView webView = new WebView();
        webView.setPrefHeight(250);
        webView.setId(uiFieldDTO.getId());

        String content = getContent(langCode);
        contentHash.put(langCode, CryptoUtil.computeFingerPrint(content, null));
        webView.getEngine().loadContent(content);
        webView.getEngine()
                .documentProperty()
                .addListener((observableValue, oldValue, document) -> addListeners(document));

        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                adjustWebViewHeight(webView, vbox, titledPane);
            }
        });

        if (titledPane != null) {
            titledPane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                if (isExpanded) {
                    adjustWebViewHeight(webView, vbox, titledPane);
                }
            });
        }

        webView.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (newWidth != null && newWidth.doubleValue() > 100 && titledPane != null && titledPane.isExpanded()) {
                adjustWebViewHeight(webView, vbox, titledPane);
            }
        });

        changeNodeOrientation(webView, langCode);
        vbox.getChildren().add(webView);
        return vbox;
    }

    private void adjustWebViewHeight(WebView webView, VBox vbox, TitledPane titledPane) {
        javafx.application.Platform.runLater(() -> {
            try {
                String script = "(function() {" +
                        "  try {" +
                        "    var body = document.body;" +
                        "    var html = document.documentElement;" +
                        "    if (!body) return 250;" +
                        "    body.style.overflow = 'hidden';" +
                        "    html.style.overflow = 'hidden';" +
                        "    var rangeHeight = 0;" +
                        "    if (document.createRange) {" +
                        "      var range = document.createRange();" +
                        "      range.selectNodeContents(body);" +
                        "      var rect = range.getBoundingClientRect();" +
                        "      if (rect && rect.height > 0) rangeHeight = Math.ceil(rect.height);" +
                        "    }" +
                        "    var maxChildBottom = 0;" +
                        "    if (body.children) {" +
                        "      for (var i = 0; i < body.children.length; i++) {" +
                        "        var el = body.children[i];" +
                        "        var b = el.offsetTop + el.offsetHeight;" +
                        "        if (b > maxChildBottom) maxChildBottom = b;" +
                        "      }" +
                        "    }" +
                        "    return Math.max(rangeHeight, maxChildBottom, body.scrollHeight || 0);" +
                        "  } catch (e) { return 250; }" +
                        "})();";
                Object result = webView.getEngine().executeScript(script);
                if (result instanceof Number) {
                    double contentHeight = ((Number) result).doubleValue();
                    if (contentHeight > 40 && contentHeight < 2000) {
                        double targetHeight = contentHeight + 25;
                        webView.setPrefHeight(targetHeight);
                        webView.setMinHeight(targetHeight);
                        webView.setMaxHeight(targetHeight);
                        if (vbox != null) {
                            vbox.requestLayout();
                        }
                        if (titledPane != null) {
                            titledPane.requestLayout();
                            javafx.scene.Parent parent = titledPane.getParent();
                            while (parent != null) {
                                parent.requestLayout();
                                parent = parent.getParent();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Could not calculate content height for webView", e);
            }
        });
    }

    private String getContent(String langCode) {
        String templateText = DEFAULT_TEMPLATE;
        try {
            LOGGER.info("Fetching template {} to prepare webview content in {} language",
                    this.uiFieldDTO.getTemplateName(), langCode);
            templateText = templateService.getHtmlTemplate(this.uiFieldDTO.getTemplateName(), langCode);
            Writer writer = new StringWriter();
            TemplateManager templateManager = templateManagerBuilder.build();
            InputStream inputStream = templateManager.merge(new ByteArrayInputStream(templateText == null ?
                            DEFAULT_TEMPLATE.getBytes(StandardCharsets.UTF_8) :
                            templateText.getBytes(StandardCharsets.UTF_8)),
                    getRegistrationDTo().getMVELDataContext());
            IOUtils.copy(inputStream, writer, StandardCharsets.UTF_8);
            return writer.toString();
        } catch (RegBaseCheckedException | IOException e) {
            LOGGER.error("Failed to build HTMLFxControl", e);
        }
        return templateText;
    }

    private void addListeners(Document document) {
        //TODO
    }
}
