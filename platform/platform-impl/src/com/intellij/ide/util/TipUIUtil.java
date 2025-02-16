// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.DynamicBundle;
import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ResourceUtil;
import com.intellij.util.SVGLoader;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.intellij.DynamicBundle.findLanguageBundle;
import static com.intellij.util.ui.UIUtil.drawImage;

/**
 * @author dsl
 * @author Konstantin Bulenkov
 */
public final class TipUIUtil {
  private static final Logger LOG = Logger.getInstance(TipUIUtil.class);
  private static final String SHORTCUT_ENTITY = "&shortcut:";
  private static final String MISC_PATH = "misc";

  private TipUIUtil() {
  }

  public static @NlsSafe @NotNull String getPoweredByText(@NotNull TipAndTrickBean tip) {
    PluginDescriptor descriptor = tip.getPluginDescriptor();
    return descriptor == null ||
           PluginManagerCore.CORE_ID.equals(descriptor.getPluginId()) ?
           "" :
           descriptor.getName();
  }

  @Nullable
  public static TipAndTrickBean getTip(@Nullable FeatureDescriptor feature) {
    if (feature == null) {
      return null;
    }

    String tipFileName = feature.getTipFileName();
    if (tipFileName == null) {
      LOG.warn("No Tip of the day for feature " + feature.getId());
      return null;
    }

    TipAndTrickBean tip = TipAndTrickBean.findByFileName("neue-" + tipFileName);
    if (tip == null && StringUtil.isNotEmpty(tipFileName)) {
      tip = TipAndTrickBean.findByFileName(tipFileName);
    }
    if (tip == null && StringUtil.isNotEmpty(tipFileName)) {
      tip = new TipAndTrickBean();
      tip.fileName = tipFileName;
    }
    return tip;
  }

  static @NlsSafe String getTipText(@Nullable TipAndTrickBean tip, Component component) {
    if (tip == null) return IdeBundle.message("no.tip.of.the.day");
    try {
      StringBuilder text = new StringBuilder();
      String cssText;
      File tipFile = new File(tip.fileName);
      if (tipFile.isAbsolute() && tipFile.exists()) {
        text.append(FileUtil.loadFile(tipFile, StandardCharsets.UTF_8));
        updateImages(text, null, tipFile.getParentFile().getAbsolutePath(), component);
        cssText = FileUtil.loadFile(new File(tipFile.getParentFile(), StartupUiUtil.isUnderDarcula()
                                                                      ? "css/tips_darcula.css" : "css/tips.css"));
      }
      else {
        PluginDescriptor pluginDescriptor = tip.getPluginDescriptor();
        ClassLoader tipLoader = pluginDescriptor != null ?
                                ObjectUtils.notNull(pluginDescriptor.getClassLoader(), TipUIUtil.class.getClassLoader()) :
                                TipUIUtil.class.getClassLoader();

        DynamicBundle.LanguageBundleEP langBundle = findLanguageBundle();
        if (langBundle != null) {
          tipLoader = langBundle.pluginDescriptor.getClassLoader();
        }

        String ideCode = ApplicationInfoEx.getInstanceEx().getApiVersionAsNumber().getProductCode().toLowerCase(Locale.ROOT);
        String tipsPath = "/tips/" + (langBundle != null ? ideCode + "/" : "");
        InputStream tipStream = ResourceUtil.getResourceAsStream(tipLoader, tipsPath, tip.fileName);

        boolean isMisc = false;
        if (tipStream == null) {
          tipStream = ResourceUtil.getResourceAsStream(tipLoader, "/tips/" + MISC_PATH + "/", tip.fileName);
          if (tipStream == null) {
            return getCantReadText(tip);
          }
          isMisc = true;
        }
        text.append(ResourceUtil.loadText(tipStream));
        String path = langBundle != null ? isMisc ? MISC_PATH : ideCode : "";
        updateImages(text, tipLoader, path, component);

        String cssPath = "/tips/" + (langBundle != null ? (isMisc ? MISC_PATH : ideCode) + "/" : "");
        InputStream cssResourceStream = ResourceUtil.getResourceAsStream(tipLoader, cssPath, StartupUiUtil.isUnderDarcula()
                                                                                             ? "css/tips_darcula.css" : "css/tips.css");
        cssText = cssResourceStream != null ? ResourceUtil.loadText(cssResourceStream) : "";
      }

      updateShortcuts(text, tip.toString());
      String replaced = text.toString().replace("&productName;", ApplicationNamesInfo.getInstance().getFullProductName());
      String major = ApplicationInfo.getInstance().getMajorVersion();
      replaced = replaced.replace("&majorVersion;", major);
      String minor = ApplicationInfo.getInstance().getMinorVersion();
      replaced = replaced.replace("&minorVersion;", minor);
      replaced = replaced.replace("&majorMinorVersion;", major + ("0".equals(minor) ? "" : ("." + minor)));
      replaced = replaced.replace("&settingsPath;", CommonBundle.settingsActionPath());
      String inlinedCSS = cssText + "\nbody {background-color:#" + ColorUtil.toHex(UIUtil.getTextFieldBackground())+ ";overflow:hidden;}";
      return replaced.replaceFirst("<link.*\\.css\">", "<style type=\"text/css\">\n" + inlinedCSS + "\n</style>");
    }
    catch (IOException e) {
      return getCantReadText(tip);
    }

  }

  public static void openTipInBrowser(@Nullable TipAndTrickBean tip, TipUIUtil.Browser browser) {
    browser.setText(getTipText(tip, browser.getComponent()));
  }

  private static @NotNull String getCantReadText(@NotNull TipAndTrickBean bean) {
    String plugin = getPoweredByText(bean);
    String product = ApplicationNamesInfo.getInstance().getFullProductName();
    if (!plugin.isEmpty()) {
      product += " and " + plugin + " plugin";
    }
    return IdeBundle.message("error.unable.to.read.tip.of.the.day", bean.fileName, product);
  }

  private static void updateImages(StringBuilder text, ClassLoader tipLoader, String tipPath, Component component) {
    final boolean dark = StartupUiUtil.isUnderDarcula();

    int index = text.indexOf("<img", 0);
    while (index != -1) {
      final int end = text.indexOf(">", index + 1);
      if (end == -1) return;
      final String img = text.substring(index, end + 1).replace('\r', ' ').replace('\n', ' ');
      int srcIndex = img.indexOf("src=\"");
      int endIndex = img.indexOf("\"", srcIndex + 6);
      if (srcIndex == -1 && endIndex == -1) {
        srcIndex = img.indexOf("src='");
        endIndex = img.indexOf("'", srcIndex + 6);
      }
      if (endIndex != -1) {
        String src = img.substring(srcIndex + 5, endIndex);
        String srcWithoutExtension = FileUtil.getNameWithoutExtension(src);

        if (!srcWithoutExtension.endsWith("_dark") && !srcWithoutExtension.endsWith("@2x") || tipLoader == null) {
          boolean hidpi = JBUI.isPixHiDPI(component);
          String suffix = (dark ? "_dark" : "") + "." + FileUtilRt.getExtension(src);
          String path = srcWithoutExtension + suffix;
          String path2x = srcWithoutExtension + "@2x" + suffix;
          String canonicalPath = getImageCanonicalPath(hidpi ? path2x : path, tipLoader, tipPath);
          try {
            boolean fallbackUpscale = false;
            boolean fallbackDownscale = false;

            BufferedImage image;
            URL actualURL;
            try {
              actualURL = new URL(canonicalPath);
              image = read(actualURL);
            }
            catch (IOException e) {
              if (hidpi) {
                fallbackUpscale = true;
                actualURL = new URL(getImageCanonicalPath(path, tipLoader, tipPath));
              }
              else {
                fallbackDownscale = true;
                actualURL = new URL(getImageCanonicalPath(path2x, tipLoader, tipPath));
              }
              image = read(actualURL);
            }

            String newImgTag;
            newImgTag = "<img src=\"" + actualURL.toExternalForm() + "\" ";
            int w = image.getWidth();
            int h = image.getHeight();
            if (hidpi) {
              // the expected (user space) size is @2x / 2 in either JRE-HiDPI or IDE-HiDPI mode
              float k = 2f;
              if (StartupUiUtil.isJreHiDPI(component)) {
                // in JRE-HiDPI mode we want the image to be drawn in its original size w/h, for better quality
                k = JBUIScale.sysScale(component);
              }
              w /= k;
              h /= k;
            }
            // round the user scale for better quality
            int userScale = RoundingMode.ROUND_FLOOR_BIAS.round(JBUIScale.scale(1f));
            w = userScale * w;
            h = userScale * h;
            if (fallbackUpscale) {
              w *= 2;
              h *= 2;
            }
            else if (fallbackDownscale) {
              w /= 2;
              h /= 2;
            }
            newImgTag += "width=\"" + w + "\" height=\"" + h + "\"";
            newImgTag += ">";
            text.replace(index, end + 1, newImgTag);
          }
          catch (Exception ignore) {
            LOG.warn("Cannot find icon with path [" + src + "]");
          }
        }
      }
      index = text.indexOf("<img", index + 1);
    }
  }

  private static @NotNull String getImageCanonicalPath(@NotNull String path, @Nullable ClassLoader tipLoader, @NotNull String tipPath) {
    try {
      URL url = tipLoader == null ? new File(tipPath, path).toURI().toURL() : ResourceUtil.getResource(tipLoader, "tips", path);
      return url == null ? path : url.toExternalForm();
    }
    catch (MalformedURLException e) {
      return path;
    }
  }

  private static BufferedImage read(@NotNull URL url) throws IOException {
    try (InputStream stream = url.openStream()) {
      BufferedImage image = ImageIO.read(stream);
      if (image == null) throw new IOException("Cannot read image with ImageIO: " + url.toExternalForm());
      return image;
    }
  }

  private static void updateShortcuts(StringBuilder text, String tipDescription) {
    int lastIndex = 0;
    while(true) {
      lastIndex = text.indexOf(SHORTCUT_ENTITY, lastIndex);
      if (lastIndex < 0) return;
      final int actionIdStart = lastIndex + SHORTCUT_ENTITY.length();
      int actionIdEnd = text.indexOf(";", actionIdStart);
      if (actionIdEnd < 0) {
        return;
      }
      final String actionId = text.substring(actionIdStart, actionIdEnd);
      String shortcutText = getShortcutText(actionId, KeymapManager.getInstance().getActiveKeymap());
      if (shortcutText == null) {
        Keymap defKeymap = KeymapManager.getInstance().getKeymap(DefaultKeymap.Companion.getInstance().getDefaultKeymapName());
        if (defKeymap != null) {
          shortcutText = getShortcutText(actionId, defKeymap);
          if (shortcutText != null) {
            shortcutText += " in default keymap";
          }
        }
      }
      if (shortcutText == null) {
        LOG.warn(tipDescription + ": no shortcut is assigned for the action " + actionId);
        shortcutText = "&lt;No shortcut is assigned for the action&gt;";
      }
      text.replace(lastIndex, actionIdEnd + 1, shortcutText);
      lastIndex += shortcutText.length();
    }
  }

  @Nullable
  private static String getShortcutText(String actionId, Keymap keymap) {
    for (final Shortcut shortcut : keymap.getShortcuts(actionId)) {
      if (shortcut instanceof KeyboardShortcut) {
        return KeymapUtil.getShortcutText(shortcut);
      }
    }
    return null;
  }

  public static Browser createBrowser() {
    return new SwingBrowser();
  }

  public interface Browser extends TextAccessor {
    void load(String url) throws IOException;
    JComponent getComponent();

    @Override
    void setText(@Nls String text);
  }

  private static class SwingBrowser extends JEditorPane implements Browser {
    SwingBrowser() {
      setEditable(false);
      setBackground(UIUtil.getTextFieldBackground());
      addHyperlinkListener(
        new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              BrowserUtil.browse(e.getURL());
            }
          }
        }
      );
      HTMLEditorKit kit = new JBHtmlEditorKit(false) {
        private final ViewFactory myFactory = createViewFactory();
        //SVG support
        private ViewFactory createViewFactory() {
          return new HTMLEditorKit.HTMLFactory() {
            @Override
            public View create(Element elem) {
              View view = super.create(elem);
              if (view instanceof ImageView) {
                String src = (String)view.getElement().getAttributes().getAttribute(HTML.Attribute.SRC);
                if (src != null /*&& src.endsWith(".svg")*/) {
                  final Image image;
                  try {
                    final URL url = new URL(src);
                    Dictionary cache = (Dictionary)elem.getDocument().getProperty("imageCache");
                    if (cache == null) {
                      elem.getDocument().putProperty("imageCache", cache = new Dictionary() {
                        private final HashMap myMap = new HashMap();
                        @Override
                        public int size() {
                          return myMap.size();
                        }

                        @Override
                        public boolean isEmpty() {
                          return size() ==0;
                        }

                        @Override
                        public Enumeration keys() {
                          return Collections.enumeration(myMap.keySet());
                        }

                        @Override
                        public Enumeration elements() {
                          return Collections.enumeration(myMap.values());
                        }

                        @Override
                        public Object get(Object key) {
                          return myMap.get(key);
                        }

                        @Override
                        public Object put(Object key, Object value) {
                          return myMap.put(key, value);
                        }

                        @Override
                        public Object remove(Object key) {
                          return myMap.remove(key);
                        }
                      });
                    }
                    image = src.endsWith(".svg")
                            ? SVGLoader.load(url, JBUI.isPixHiDPI((Component)null) ? 2f : 1f)
                            : Toolkit.getDefaultToolkit().createImage(url);
                    cache.put(url, image);
                    if (src.endsWith(".svg"))
                      return new ImageView(elem) {
                        @Override
                        public Image getImage() {
                          return image;
                        }

                        @Override
                        public URL getImageURL() {
                          return url;
                        }

                        @Override
                        public void paint(Graphics g, Shape a) {
                          Rectangle bounds = a.getBounds();
                          int width = (int)getPreferredSpan(View.X_AXIS);
                          int height = (int)getPreferredSpan(View.Y_AXIS);
                          @SuppressWarnings("UndesirableClassUsage")
                          BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                          Graphics2D graphics = buffer.createGraphics();
                          super.paint(graphics, new Rectangle(buffer.getWidth(), buffer.getHeight()));
                          drawImage(g, ImageUtil.ensureHiDPI(image, ScaleContext.create((Component)null)), bounds.x, bounds.y, null);
                        }

                        @Override
                        public float getMaximumSpan(int axis) {
                          return getPreferredSpan(axis);
                        }

                        @Override
                        public float getMinimumSpan(int axis) {
                          return getPreferredSpan(axis);
                        }

                        @Override
                        public float getPreferredSpan(int axis) {
                          return (axis == View.X_AXIS ? image.getWidth(null) : image.getHeight(null)) / JBUIScale.sysScale();
                        }
                      };
                  }
                  catch (IOException e) {
                    //ignore
                  }
                }
              }
              return view;
            }
          };
        }

        @Override
        public ViewFactory getViewFactory() {
          return myFactory;
        }
      };

      String fileName = StartupUiUtil.isUnderDarcula() ? "tips_darcula.css" : "tips.css";
      URL resource = TipUIUtil.class.getClassLoader().getResource("tips/css/" + fileName);
      kit.getStyleSheet().addStyleSheet(StartupUiUtil.loadStyleSheet(resource));
      setEditorKit(kit);
    }

    @Override
    public void setText(String t) {
      super.setText(t);
      if (t != null && t.length()>0) {
        setCaretPosition(0);
      }
    }

    @Override
    public void load(String url) throws IOException{
      @NlsSafe String text = IOUtil.readString(new DataInputStream(new URL(url).openStream()));
      setText(text);
    }

    @Override
    public JComponent getComponent() {
      return this;
    }
  }
}
