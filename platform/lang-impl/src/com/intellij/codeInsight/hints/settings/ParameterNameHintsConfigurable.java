/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.hints.settings;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hints.HintUtilsKt;
import com.intellij.codeInsight.hints.InlayParameterHintsExtension;
import com.intellij.codeInsight.hints.InlayParameterHintsProvider;
import com.intellij.codeInsight.hints.Option;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.openapi.editor.colors.CodeInsightColors.ERRORS_ATTRIBUTES;

public class ParameterNameHintsConfigurable extends DialogWrapper {

  private JPanel myConfigurable;
  private ComboBox<Language> myCurrentLanguageCombo;
  
  private Map<Language, EditorTextField> myEditors;
  private Map<Language, Boolean> myIsValidPatterns;
  private Map<Option, JBCheckBox> myOptions;
  
  private JPanel myPanel;
  private CardLayout myCardLayout;

  public ParameterNameHintsConfigurable() {
    this(null, null);
  }

  public ParameterNameHintsConfigurable(@Nullable Language selectedLanguage,
                                        @Nullable String newPreselectedPattern) {
    super(null);
    setTitle("Configure Parameter Name Hints");
    init();
    
    if (selectedLanguage != null) {
      showLanguagePanel(selectedLanguage);
      myCurrentLanguageCombo.setSelectedItem(selectedLanguage);
      if (newPreselectedPattern != null) {
        addSelectedText(selectedLanguage, newPreselectedPattern);
      }
    }
  }

  private void addSelectedText(@NotNull Language language, @NotNull String newPreselectedPattern) {
    EditorTextField textField = myEditors.get(language);

    String text = textField.getText();
    int startOffset = text.length();
    text += "\n" + newPreselectedPattern;
    int endOffset = text.length();

    textField.setText(text);
    textField.addSettingsProvider((editor) -> {
      SelectionModel model = editor.getSelectionModel();
      model.setSelection(startOffset + 1, endOffset);
    });
  }

  private void updateOkEnabled(@NotNull Language language, @NotNull EditorTextField editorTextField) {
    String text = editorTextField.getText();
    List<Integer> invalidLines = HintUtilsKt.getBlackListInvalidLineNumbers(text);
    
    myIsValidPatterns.put(language, invalidLines.isEmpty());
    boolean isEveryOneValid = !myIsValidPatterns.containsValue(false);
    
    getOKAction().setEnabled(isEveryOneValid);
    highlightErrorLines(invalidLines, editorTextField);
  }

  private static void highlightErrorLines(@NotNull List<Integer> lines, @NotNull EditorTextField editorTextField) {
    Editor editor = editorTextField.getEditor();
    if (editor == null) return;

    final TextAttributes attributes = editor.getColorsScheme().getAttributes(ERRORS_ATTRIBUTES);
    final Document document = editor.getDocument();
    final int totalLines = document.getLineCount();

    MarkupModel model = editor.getMarkupModel();
    model.removeAllHighlighters();
    lines.stream()
      .filter((current) -> current < totalLines)
      .forEach((line) -> model.addLineHighlighter(line, HighlighterLayer.ERROR, attributes));
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    
    myEditors.forEach((language, editor) -> {
      String blacklist = editor.getText();
      storeBlackListDiff(language, blacklist);
    });
    
    myOptions.forEach((option, checkBox) -> option.set(checkBox.isSelected()));
  }

  private static void storeBlackListDiff(@NotNull Language language, @NotNull String text) {
    Set<String> updatedBlackList = StringUtil
      .split(text, "\n")
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .collect(Collectors.toCollection(LinkedHashSet::new));

    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    Set<String> defaultBlackList = provider.getDefaultBlackList();
    Diff diff = Diff.Builder.build(defaultBlackList, updatedBlackList);
    ParameterNameHintsSettings.getInstance().setBlackListDiff(language, diff);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myConfigurable;
  }

  private void createUIComponents() {
    myOptions = ContainerUtil.newHashMap();
    myEditors = ContainerUtil.newHashMap();
    myIsValidPatterns = ContainerUtil.newHashMap();

    List<Language> allLanguages = getBaseLanguagesWithProviders();
    Language selected = allLanguages.get(0);

    initLanguageCombo(selected, allLanguages);

    myCardLayout = new CardLayout();
    myPanel = new JPanel(myCardLayout);

    allLanguages.forEach((language -> {
      JPanel panel = createLanguagePanel(language);
      myPanel.add(panel, language.getDisplayName());
    }));

    myCardLayout.show(myPanel, selected.getDisplayName());
  }

  @NotNull
  private JPanel createLanguagePanel(@NotNull Language language) {
    JPanel blacklistPanel = createBlacklistPanel(language);
    JPanel optionsPanel = createOptionsPanel(language);

    JPanel panel = new JPanel();
    BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
    panel.setLayout(layout);

    if (blacklistPanel != null) {
      panel.add(blacklistPanel);
    }

    if (optionsPanel != null) {
      panel.add(optionsPanel);
    }
    
    return panel;
  }

  @Nullable
  private JPanel createBlacklistPanel(@NotNull Language language) {
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (!provider.isBlackListSupported()) return null;

    String blackList = getLanguageBlackList(language);

    EditorTextField editorTextField = createEditorField(blackList);
    editorTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        updateOkEnabled(language, editorTextField);
      }
    });
    updateOkEnabled(language, editorTextField);
    
    myEditors.put(language, editorTextField);

    JPanel blacklistPanel = new JPanel();

    BoxLayout layout = new BoxLayout(blacklistPanel, BoxLayout.Y_AXIS);
    blacklistPanel.setLayout(layout);
    blacklistPanel.setBorder(IdeBorderFactory.createTitledBorder("Blacklist"));
    
    blacklistPanel.add(new JBLabel(getBlacklistExplanationHTML(language)));
    blacklistPanel.add(editorTextField);

    return blacklistPanel;
  }

  @NotNull
  private String getBlacklistExplanationHTML(Language language) {
    InlayParameterHintsProvider hintsProvider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (hintsProvider == null) {
      return CodeInsightBundle.message("inlay.hints.blacklist.pattern.explanation");
    }
    return hintsProvider.getBlacklistExplanationHTML();
  }

  @Nullable
  private JPanel createOptionsPanel(@NotNull Language language) {
    List<Option> options = getOptions(language);
    if (options.isEmpty()) {
      return null;
    }

    JPanel languageOptionsPanel = new JPanel();
    BoxLayout boxLayout = new BoxLayout(languageOptionsPanel, BoxLayout.Y_AXIS);
    languageOptionsPanel.setLayout(boxLayout);

    if (!options.isEmpty()) {
      languageOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder("Options"));
    }

    for (Option option : options) {
      JBCheckBox box = new JBCheckBox(option.getName(), option.get());
      myOptions.put(option, box);
      languageOptionsPanel.add(box);
    }

    return languageOptionsPanel;
  }

  private void initLanguageCombo(Language selected, List<Language> languages) {
    ListComboBoxModel<Language> model = new ListComboBoxModel<>(languages);
    
    myCurrentLanguageCombo = new ComboBox<>(model);
    myCurrentLanguageCombo.setSelectedItem(selected);
    myCurrentLanguageCombo.setRenderer(new ListCellRendererWrapper<Language>() {
      @Override
      public void customize(JList list, Language value, int index, boolean selected, boolean hasFocus) {
        setText(value.getDisplayName());
      }
    });

    myCurrentLanguageCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        Language language = (Language)e.getItem();
        if (e.getStateChange() == ItemEvent.SELECTED) {
          showLanguagePanel(language);
        }
      }
    });
  }

  private void showLanguagePanel(@NotNull Language language) {
    myCardLayout.show(myPanel, language.getDisplayName());
  }
  
  private static List<Option> getOptions(Language language) {
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider != null) {
      return provider.getSupportedOptions();
    }
    return ContainerUtil.emptyList();
  }

  @NotNull
  private static String getLanguageBlackList(@NotNull Language language) {
    InlayParameterHintsProvider hintsProvider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (hintsProvider == null) {
      return "";
    }
    Diff diff = ParameterNameHintsSettings.getInstance().getBlackListDiff(language);
    Set<String> blackList = diff.applyOn(hintsProvider.getDefaultBlackList());
    return StringUtil.join(blackList, "\n");
  }

  @NotNull
  private static List<Language> getBaseLanguagesWithProviders() {
    return HintUtilsKt
      .getHintProviders()
      .stream()
      .map((langWithImplementation) -> langWithImplementation.getFirst())
      .sorted(Comparator.comparingInt(l -> l.getDisplayName().length()))
      .collect(Collectors.toList());
  }
  
  @NotNull
  private static EditorTextField createEditorField(@NotNull String text) {
    Document document = EditorFactory.getInstance().createDocument(text);
    EditorTextField field = new EditorTextField(document, null, FileTypes.PLAIN_TEXT, false, false);
    field.setPreferredSize(new Dimension(200, 350));
    field.addSettingsProvider(editor -> {
      editor.setVerticalScrollbarVisible(true);
      editor.setHorizontalScrollbarVisible(true);
      editor.getSettings().setAdditionalLinesCount(2);
    });
    return field;
  }
}
