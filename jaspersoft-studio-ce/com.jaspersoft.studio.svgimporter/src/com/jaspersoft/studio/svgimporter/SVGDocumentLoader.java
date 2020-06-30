/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.svgimporter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.anim.dom.SVGOMElement;
import org.apache.batik.anim.dom.SVGOMEllipseElement;
import org.apache.batik.anim.dom.SVGOMGElement;
import org.apache.batik.anim.dom.SVGOMImageElement;
import org.apache.batik.anim.dom.SVGOMMaskElement;
import org.apache.batik.anim.dom.SVGOMPathElement;
import org.apache.batik.anim.dom.SVGOMRectElement;
import org.apache.batik.anim.dom.SVGOMSVGElement;
import org.apache.batik.anim.dom.SVGOMTSpanElement;
import org.apache.batik.anim.dom.SVGOMTextElement;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.dom.GenericText;
import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.parser.PathParser;
import org.apache.batik.parser.TransformListParser;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.apache.batik.util.Base64DecodeStream;
import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.commons.lang.StringUtils;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSValue;
import org.w3c.dom.css.RGBColor;
import org.w3c.dom.svg.SVGPaint;
import org.w3c.dom.svg.SVGRect;
import org.w3c.dom.svg.SVGSVGElement;

import com.jaspersoft.studio.JaspersoftStudioPlugin;
import com.jaspersoft.studio.svgimporter.CreateResourceCommand;
import com.jaspersoft.studio.svgimporter.InkscapeSVGDocumentFactory;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;

import net.sf.jasperreports.eclipse.util.FileUtils;
import net.sf.jasperreports.eclipse.util.Pair;
import net.sf.jasperreports.engine.JRCommonText;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignEllipse;
import net.sf.jasperreports.engine.design.JRDesignImage;
import net.sf.jasperreports.engine.design.JRDesignLine;
import net.sf.jasperreports.engine.design.JRDesignRectangle;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.fill.JRMeasuredText;
import net.sf.jasperreports.engine.fill.JRTextMeasurer;
import net.sf.jasperreports.engine.fonts.FontUtil;
import net.sf.jasperreports.engine.type.LineDirectionEnum;
import net.sf.jasperreports.engine.type.RotationEnum;
import net.sf.jasperreports.engine.util.JRStyledText;
import net.sf.jasperreports.engine.util.JRStyledTextParser;
import net.sf.jasperreports.engine.util.JRStyledTextUtil;
import net.sf.jasperreports.engine.util.JRTextMeasurerUtil;

/**
 * Class to convert an SVG file into a series of JRDesignElement. 
 * 
 * @author Orlandin Marco
 *
 */
public class SVGDocumentLoader {

  private static final double degree = 180 / Math.PI;
  
  private static final double radian = Math.PI / 180;
	
  /**
   * Prefix of the image imported into the workspace, because for the SVG are stored as byte blob in base64
   */
  protected static final String IMPORTED_IMAGE_PREFIX = "imported_image_"; //$NON-NLS-1$
  
  /**
   * The loaded document
   */
  private Document svgDocument;
  
  /**
   * The {@link JasperReportsConfiguration} of the report where the elements will be created
   */
  private JasperReportsConfiguration jConfig;
  
  /**
   * The list of resources (static images) found in the svg file. The resources are stored in the system temp folder
   * and can be recovered when executing the command to create the elements
   */
  private List<Pair<File, JRDesignImage>> resources = new ArrayList<>();
  
  /**
   * For performance reasons the masks loaded during the execution are keep cached. They are loaded in a lazy way
   * so the caching has no negative impact on performances
   */
  private HashMap<String, List<BufferedImage>> masks = new HashMap<>();

  /**
   * Creates an SVG Document given a URI.
   *
   * @param uri Path to the file.
   * @throws Exception Something went wrong parsing the SVG file.
   */
  public SVGDocumentLoader(String uri, JasperReportsConfiguration jConfig) throws IOException {
    setSVGDocument( createSVGDocument( uri ) );
    this.jConfig = jConfig;
  }
  
  protected String createSpacesString(int numberOfSpaces) {
	  char[] chars = new char[numberOfSpaces];
	  Arrays.fill(chars,' ');
	  return new String(chars);
  }
  
  protected int getSplitPoint(String text, int startPoint) {
	  char[] chars = text.toCharArray();
	  for(int i = startPoint ; i < chars.length ; i++) {
		  char c = chars[i];
		  if (c == ' ') {
			  return i;
		  } else if (c == '.' || c == ',') {
			  return i+1;
		  }
	  }
	  return -1;
  }
  
  /**
   * Look in the passed svg element for its transformation matrix and return 
   * the affine transform of it
   * 
   * @param element a not null SVG element
   * @return a not null affine transform
   */
  private AffineTransform getTransofrm(SVGOMElement element) {
	  TransformListParser transformMatrix = new TransformListParser();
      AWTTransformProducer matrixProducer = new AWTTransformProducer();
      transformMatrix.setTransformListHandler(matrixProducer);
      transformMatrix.parse(element.getAttribute("transform")); //$NON-NLS-1$
      return matrixProducer.getAffineTransform();
  }
 
  /**
   * check if a {@link CSSStyleDeclaration} has the attribute to be bold
   * 
   * @param style a not null {@link CSSStyleDeclaration}
   * @return true if the style has the bold attribute, false otherwise
   */
  private boolean isBold(CSSStyleDeclaration style) {
	  String fontWeight = style.getPropertyValue("font-weight"); //$NON-NLS-1$
	  if (fontWeight != null && !fontWeight.trim().isEmpty()) {
		  if (fontWeight.equalsIgnoreCase("bold") || fontWeight.equalsIgnoreCase("bolder")) { //$NON-NLS-1$ //$NON-NLS-2$
			  return true;
		  }
		  if (StringUtils.isNumeric(fontWeight)) {
			  try {
				  int weight = Integer.parseInt(fontWeight);
				  return weight > 500;
			  } catch (Exception ex) {
				  ex.printStackTrace();
			  }
		  }
	  } 
	  return false;
  }
  
  /**
   * check a {@link CSSStyleDeclaration} for the stroke width
   * 
   * @param style a not null {@link CSSStyleDeclaration}
   * @return the float value of the stroke or 0 if it can't be found
   */
  private float getStrokeWidth(CSSStyleDeclaration style) {
	  String strokeWidth = style.getPropertyValue("stroke-width"); //$NON-NLS-1$
	  if (StringUtils.isNumeric(strokeWidth)) {
		  try {
			  return Float.parseFloat(strokeWidth);
		  } catch (Exception ex) {
			  ex.printStackTrace();
		  }
	  }
	  return 0f;
  }
  
  /**
   * check if a {@link CSSStyleDeclaration} has the attribute to be italic
   * 
   * @param style a not null {@link CSSStyleDeclaration}
   * @return true if the style has the italic attribute, false otherwise
   */
  private boolean isItalic(CSSStyleDeclaration style) {
	  String fontWeight = style.getPropertyValue("font-style"); //$NON-NLS-1$
	  if (fontWeight != null && !fontWeight.trim().isEmpty()) {
		  if (fontWeight.equalsIgnoreCase("oblique") || fontWeight.equalsIgnoreCase("italic")) { //$NON-NLS-1$ //$NON-NLS-2$
			  return true;
		  }
	  } 
	  return false;
  }
  
  public Color getColor(SVGPaint svgPaint) { 
	  Color returnColor = null;
      if (svgPaint != null && svgPaint.getPaintType() == SVGPaint.SVG_PAINTTYPE_RGBCOLOR) { 
          final RGBColor rgb = svgPaint.getRGBColor(); 
          final float red = rgb.getRed().getFloatValue(CSSValue.CSS_PRIMITIVE_VALUE); 
          final float green = rgb.getGreen().getFloatValue(CSSValue.CSS_PRIMITIVE_VALUE); 
          final float blue = rgb.getBlue().getFloatValue(CSSValue.CSS_PRIMITIVE_VALUE); 
          returnColor = new Color(red / 255, green / 255, blue / 255); 
      }
      return returnColor; 
  } 
  
  /** 
   * Returns the color of the given attribute in the given element. 
   * 
   * @param style a not null {@link CSSStyleDeclaration}
   * @param attribute The name of the attribute (fill, stroke, etc.) 
   * 
   * @return An instance of Color. 
   */ 
  public Color getColor(CSSStyleDeclaration style, String attribute) { 
	  SVGPaint svgPaint = (SVGPaint) style.getPropertyCSSValue(attribute); 
      return getColor(svgPaint); 
  } 
  
  @SuppressWarnings("unused")
  private int getAwtFontStyle(boolean isBold, boolean isItalic) {
	  int result = Font.PLAIN;
	  if (isBold) {
		  result = result | Font.BOLD;
	  }
	  if (isItalic) {
		  result = result | Font.ITALIC;
	  }
	  return result;
  }
  
  /**
   * Parse the coordinate value converting them from string to value
   * 
   * @param value the string value
   * @return the value as double or 0 if the value is null or empty
   */
  private Number parseNumber(String value) {
	  if (value == null || value.trim().isEmpty()) return null;
	  try {
		  return Double.parseDouble(value);
	  }catch(Exception ex) {
		  ex.printStackTrace();
	  }
	  return null;
  }
  
  /**
   * Parse the coordinate value converting them from string to value
   * 
   * @param value the string value
   * @return the value as double or 0 if the value is null or empty
   */
  private double parseCoordinates(String value) {
	  if (value == null || value.trim().isEmpty()) return 0;
	  if (value.contains(" ")) { //$NON-NLS-1$
		  value = value.split(" ")[0]; //$NON-NLS-1$
	  }
	  return Double.parseDouble(value);
  }
  
  /**
   * Parse a list of coordinates converting them from string to value
   * 
   * @param value the string value
   * @return a list of double, can be null
   */
  private List<Double> parseCoordinatesList(String value) {
	  if (value == null || value.trim().isEmpty()) return null;
	  String[] stringValues = value.split(" "); //$NON-NLS-1$
	  List<Double> result = new ArrayList<>();
	  for(String stringValue : stringValues) {
		  try {
			  result.add(Double.parseDouble(stringValue));
		  }catch (Exception ex) {
			  ex.printStackTrace();
		  }
	  }
	  return result;
  }
  
  /**
   * Return the rotation in degree given by the current transform
   * 
   * @param transform a not null transform
   * @return the rotation in degree of the element
   */
  private double getRotation(AffineTransform transform)  {
      /**
       * scaleX = sqrt(a^2+c^2)
       * scaleY = sqrt(b^2+d^2)
       * rotation = tan^-1(c/d) = tan^-1(-b/a) it will not work sometimes 
       * rotation = a / scaleX  = d / scaleY
       */
	  double a = transform.getScaleX();
	  double c = transform.getShearX();
	  double scaleX = Math.sqrt(Math.pow(a,2) + Math.pow(c, 2));

      double sign = Math.atan(-c / a);
      double rad  = Math.acos(a / scaleX);
      double deg  = rad * degree;
      double rotation;
      if (deg > 90 && sign > 0) {
           rotation = (360 - deg) * radian;
       } else if (deg < 90 && sign < 0)  {
           rotation = (360 - deg) * radian;
       }  else {
           rotation = rad;
       }
      return rotation * degree;
  }
  
  /**
   * Return the size that a {@link JRDesignStaticText} must have to contains and show the text
   * inside it
   * 
   * @param newStaticText a not null {@link JRDesignStaticText}
   * @return the size the text will have inside the static text
   */
  private JRMeasuredText getTextSize(JRDesignStaticText newStaticText) {
	  String text = newStaticText.getText();
	  JRTextMeasurer measurer = JRTextMeasurerUtil.getInstance(jConfig).createTextMeasurer(newStaticText, null);
	  Map<Attribute, Object> attributes = new HashMap<>(); 
	  FontUtil.getInstance(jConfig).getAttributesWithoutAwtFont(attributes, newStaticText);
	  JRStyledText styledText = JRStyledTextParser.getInstance().getStyledText(attributes, text, !JRCommonText.MARKUP_NONE.equals(newStaticText.getMarkup()), Locale.getDefault());
	  styledText = JRStyledTextUtil.getInstance(jConfig).resolveFonts(styledText, Locale.getDefault());
	  JRMeasuredText measuredText = measurer.measure(styledText, 0, 2000, false, false);
	  return measuredText;
  }
  
  /**
   * Generate the {@link JRDesignStaticText} with the passed attributes
   * @param text the text inside the {@link JRDesignStaticText}
   * @param style the resolved style in the SVG format
   * @param x the x position where the element will be placed
   * @param y the y position where be placed
   * @param singleCharX this is currently not used but it would be the x defined into the tspan x position when it uses an array. In svg is 
   * possible define an array of x into a tspan, this means give to position for every single character
   * @param boundingBox this is the bounding box of the text in the svg, currently this is not used
   * @param transform the current transform
   * @return a not null {@link JRDesignStaticText}
   */
  private JRDesignStaticText generateText(String text, CSSStyleDeclaration style, int x, int y, List<Double> singleCharX, SVGRect boundingBox, AffineTransform transform) {
	  if (text == null || text.trim().isEmpty()) {
		  return null;
	  } else {
		  JRDesignStaticText newStaticText = new JRDesignStaticText(); 
		  float fontSize = 9f;
		  String fontSizeText = style.getPropertyValue("font-size"); //$NON-NLS-1$
		  if (fontSizeText != null && !fontSizeText.trim().isEmpty()) {
			  if (fontSizeText.endsWith("px")) { //$NON-NLS-1$
				  fontSizeText = fontSizeText.substring(0, fontSizeText.length() - 2);
			  }
			  fontSize = Float.valueOf(fontSizeText);
		  } 
	
		  newStaticText.setFontSize(fontSize);
		  String fontFamily = style.getPropertyValue("font-family"); //$NON-NLS-1$
		  if (fontFamily != null && !fontFamily.trim().isEmpty()) {
			  newStaticText.setFontName(fontFamily);
		  } else {
			  fontFamily = "sans-serif"; //$NON-NLS-1$
		  }
		  
		  Color foregroundColor = getColor(style, "fill"); //$NON-NLS-1$
		  if (foregroundColor != null) {
			  newStaticText.setForecolor(foregroundColor);
		  }
		  
		  Boolean isBold = isBold(style);
		  newStaticText.setBold(isBold);
		  Boolean isItalic = isItalic(style);
		  newStaticText.setItalic(isItalic);
		  
		  double rotation = getRotation(transform);
		  if (rotation != 0d) {
			  if (rotation > 0d && rotation <= 90d) {
				  newStaticText.setRotation(RotationEnum.RIGHT);
			  } else if (rotation > 90d && rotation <= 180d) {
				  newStaticText.setRotation(RotationEnum.UPSIDE_DOWN);
			  } else if (rotation > 180d && rotation <= 270d) {
				  newStaticText.setRotation(RotationEnum.LEFT);
			  }
		  }
		  
		  newStaticText.setWidth(10000);
		  newStaticText.setHeight(10000);
		  newStaticText.setText(text);
		  
		  JRMeasuredText measuredText = getTextSize(newStaticText);
		  	  
		  int width = (int)Math.ceil(measuredText.getTextWidth()+1);
		  int height = (int)Math.ceil(measuredText.getTextHeight()+1);
		 /* 
		   	  Font font = new Font(fontFamily, getAwtFontStyle(isBold, isItalic), (int)Math.ceil(fontSize));
		  FontRenderContext context = new FontRenderContext(transform, false, false);
		  LineMetrics metrics = font.getLineMetrics(text, context);
		   int height = (int)Math.ceil(metrics.getHeight());
		   if (singleCharX != null && singleCharX.size() > 1) {
			  double lastCharWidth =  font.getStringBounds(text.substring(text.length()-1), context).getWidth() + 1;
			  width = (int)(Math.abs(Math.ceil(singleCharX.get(0) - singleCharX.get(singleCharX.size() - 1))) + lastCharWidth) ;
			  Rectangle2D bounds = new Rectangle2D.Float(x, y, width, height);
			  bounds = transform.createTransformedShape(bounds).getBounds2D();
			  width = (int)Math.ceil(bounds.getWidth());
		  } else {
			  Rectangle2D bounds = new Rectangle2D.Float(boundingBox.getX(), boundingBox.getY(), boundingBox.getWidth(), boundingBox.getHeight());
			  bounds = transform.createTransformedShape(bounds).getBounds2D();
			  width = (int)Math.ceil(bounds.getWidth());
		  }*/
		  
		  
		  if (newStaticText.getRotationValue() == RotationEnum.LEFT || newStaticText.getRotationValue() == RotationEnum.RIGHT) {
			  newStaticText.setHeight(width);
			  newStaticText.setWidth(height);
			  newStaticText.setY(y);
			  newStaticText.setX(x - newStaticText.getWidth() + 1);
		  } else {
			  newStaticText.setHeight(height);
			  newStaticText.setWidth(width);
			  newStaticText.setY(y - newStaticText.getHeight() + 1);
			  newStaticText.setX(x);
		  }
		  //newStaticText.setY(y - newStaticText.getHeight() + 1);
		  return newStaticText;
	  }
  }
  
  /**
   * Convert a SVG tspan element into a series of {@link JRDesignStaticText}
   * 
   * @param textNode the svg tspan
   * @param textBoundingBox the boinding box of the text
   * @param previousTransform the current transform
   * @return a not null list of {@link JRDesignStaticText} converted from the content of the span
   */
  public List<JRDesignStaticText> parseSpan(SVGOMTSpanElement textNode, SVGRect textBoundingBox, AffineTransform previousTransform) {
	  NodeList nodes = textNode.getChildNodes();
	  AffineTransform newTransform = getTransofrm(textNode);
	  previousTransform.concatenate(newTransform);
      List<JRDesignStaticText> result = new ArrayList<>();
      CSSStyleDeclaration textNodeStyle = ((SVGSVGElement)svgDocument.getDocumentElement()).getComputedStyle(textNode, null);  
	  for (int i = 0;  i < nodes.getLength();  i++) {
          Node node = nodes.item(i);
          if (node instanceof GenericText) {
        	 String text = node.getNodeValue();
        	 if (text != null) {
        		double x = parseCoordinates(textNode.getAttribute("x")); //$NON-NLS-1$
        		double y = parseCoordinates(textNode.getAttribute("y")); //$NON-NLS-1$
        		Point2D.Double point = new  Point2D.Double(x, y);
        		Point2D coordinates =  previousTransform.transform(point, null);
        		List<Double> xList = parseCoordinatesList(textNode.getAttribute("x")); //$NON-NLS-1$
        		JRDesignStaticText jrText = generateText(text, textNodeStyle, (int)coordinates.getX(), (int)coordinates.getY(), xList, textBoundingBox, previousTransform);
        		if (jrText != null) { 
        			result.add(jrText);
        		}
        	 }
          }
      }
	  return result;
  }

  /**
   * Convert a SVG text element into a series of {@link JRDesignStaticText}
   * 
   * @param textNode the svg text
   * @param previousTransform the current transform
   * @return a not null list of {@link JRDesignStaticText} converted from the content of the text
   */
  public List<JRDesignStaticText> parseText(SVGOMTextElement textNode, AffineTransform previousTransform) {
	  NodeList nodes = textNode.getChildNodes();
	  AffineTransform newTransform = getTransofrm(textNode);
	  previousTransform.concatenate(newTransform);
      List<JRDesignStaticText> result = new ArrayList<>();
      SVGRect boundingBox = textNode.getBBox();
      CSSStyleDeclaration textNodeStyle = ((SVGSVGElement)svgDocument.getDocumentElement()).getComputedStyle(textNode, null);   
	  for (int i = 0;  i < nodes.getLength();  i++) {
          Node node = nodes.item(i);
          if (node instanceof GenericText) {
        	 String text = node.getNodeValue();
        	 if (text != null) {
        		double x = parseCoordinates(textNode.getAttribute("x")); //$NON-NLS-1$
        		double y = parseCoordinates(textNode.getAttribute("y")); //$NON-NLS-1$
        		Point2D.Double point = new  Point2D.Double(x, y);
        		Point2D coordinates =  previousTransform.transform(point, null);
        		JRDesignStaticText jrText = generateText(text, textNodeStyle, (int)coordinates.getX(), (int)coordinates.getY(), null, boundingBox, previousTransform);
        		if (jrText != null) { 
        			result.add(jrText);
        		}
        	 }
          } else if (node instanceof SVGOMTSpanElement) {
        	  result.addAll(parseSpan((SVGOMTSpanElement)node, boundingBox, new AffineTransform(previousTransform)));
          }
      }
	  return result;
  }
  
  /**
   * Convert an SVG rectangle into a JR rectangle
   * 
   * @param rect the SVG rectangle
   * @param previousTransform the current transform
   * @return a not null {@link JRDesignRectangle}
   */
  private JRDesignRectangle parseRectangle(SVGOMRectElement rect, AffineTransform previousTransform) {
	  AffineTransform newTransform = getTransofrm(rect);
	  previousTransform.concatenate(newTransform);
	  double x = parseCoordinates(rect.getAttribute("x")); //$NON-NLS-1$
	  double y = parseCoordinates(rect.getAttribute("y")); //$NON-NLS-1$
	  double width = Double.parseDouble(rect.getAttribute("width")); //$NON-NLS-1$
	  double height = Double.parseDouble(rect.getAttribute("height")); //$NON-NLS-1$
	  Rectangle2D rectFigure = new Rectangle2D.Double(x, y, width, height);
	  rectFigure = (Rectangle2D)previousTransform.createTransformedShape(rectFigure).getBounds2D();
	  JRDesignRectangle rectangle = new JRDesignRectangle();
	  rectangle.setX((int)rectFigure.getX());
	  rectangle.setY((int)rectFigure.getY());
	  rectangle.setWidth((int)rectFigure.getWidth());
	  rectangle.setHeight((int)rectFigure.getHeight());
	  
	  CSSStyleDeclaration nodeStyle = ((SVGSVGElement)svgDocument.getDocumentElement()).getComputedStyle(rect, null);  
	  Color backgroundColor = getColor(nodeStyle, "fill"); //$NON-NLS-1$
	  if (backgroundColor != null) {
		  rectangle.setBackcolor(backgroundColor);
	  }
	  
	  Float strokeWidth = getStrokeWidth(nodeStyle);
	  rectangle.getLinePen().setLineWidth(strokeWidth);
	  Color strokeColor = getColor(nodeStyle, "stroke"); //$NON-NLS-1$
	  if (strokeColor != null) {
		  rectangle.getLinePen().setLineColor(strokeColor);
	  }
	  
	  return rectangle;
  }
  
  /**
   * Parse a mask for an image and return the buggered image to apply to the original image as mask. The cache 
   * for masks is used in this case
   * 
   * @param maskElement the mask element
   * @return the {@link BufferedImage} of each mask found, can be empty if no image is found but not null
   */
  private List<BufferedImage> parseMask(SVGOMMaskElement maskElement){
	  String maskId = maskElement.getAttribute("id");
	  //look first in the cache
	  List<BufferedImage> result = masks.get(maskId);
	  if (result == null) {
		  result = new ArrayList<>();
		  NodeList nodes = maskElement.getChildNodes();
		  for (int i = 0;  i < nodes.getLength();  i++) {
	          Node node = nodes.item(i);
	          if (node instanceof SVGOMMaskElement) {
	        	  result.addAll(parseMask((SVGOMMaskElement)node));
	          } else if (node instanceof SVGOMImageElement) {
	        	  SVGOMImageElement imageElement = (SVGOMImageElement)node;
	        	  String sourceData = imageElement.getAttributeNS("http://www.w3.org/1999/xlink", "href"); //$NON-NLS-1$ //$NON-NLS-2$
	        	  String[] splitData = sourceData.split(","); //$NON-NLS-1$
	        	  BufferedImage maskImage = decodeImage(imageElement, splitData[1].getBytes()) ;
	        	  if (maskElement != null) {
	        		  result.add(maskImage);
	        	  }
	          }
	      }
		  masks.put(maskId, result);
	  }
	  return result;
  }
  
  /**
   * Decode an image from a bytestream and eventually applay a mask if it is defined on its element. The mask is 
   * loaded eventually by this method that will applay a chain of masks
   * 
   * @param imageElement the image element
   * @param imageBytesArray the bytearray of the mask
   * @return a {@link BufferedImage} with applied any defined mask or null if the image can't be loaded
   */
  private BufferedImage decodeImage(SVGOMImageElement imageElement, byte[] imageBytesArray) {
	  ByteArrayInputStream imageBytes = new ByteArrayInputStream(imageBytesArray);
	  Base64DecodeStream decodeStream = new Base64DecodeStream(imageBytes);
	  // create a buffered image
	  BufferedImage image;
	  try {       
		  image = ImageIO.read(decodeStream);
		  
		  String maskRef = imageElement.getAttribute("mask");
		  if (maskRef != null) {
			  if (maskRef.startsWith("url(") && maskRef.endsWith(")")) {
				  maskRef = maskRef.substring(4, maskRef.length()-1);
				  if (maskRef.startsWith("#")) {
					  maskRef = maskRef.substring(1);
					  Element maskElement = svgDocument.getElementById(maskRef);
					  if (maskElement != null && maskElement instanceof SVGOMMaskElement) {
						  List<BufferedImage> masks = parseMask((SVGOMMaskElement)maskElement);
						  for(BufferedImage mask : masks) {
							  final int width = image.getWidth();
							  int[] imgData = new int[width];
							  int[] maskData = new int[width];

							  for (int y = 0; y < image.getHeight(); y++) { 
								  image.getRGB(0, y, width, 1, imgData, 0, 1);
							      mask.getRGB(0, y, width, 1, maskData, 0, 1);
							      for (int x = 0; x < width; x++) { 
							    	  int color = imgData[x] & 0x00FFFFFF; 
							    	  int maskColor = (maskData[x] & 0x00FF0000) << 8;
							    	  color |= maskColor;        
							    	  imgData[x] = color;    
							      }

							      //replace the data
							      image.setRGB(0, y, width, 1, imgData, 0, 1);
							  }
						  }
					  }
				  }
			  }
		  }
		  return image;
	  } catch (IOException e) {
		  e.printStackTrace();
	  } finally { 
		  FileUtils.closeStream(decodeStream);
		  FileUtils.closeStream(imageBytes);	
	  }
	  return null;
  }
  
  /**
   * Get the filename for a resource that will be saved inside the workspace. This name
   * is used for the temp folder
   * 
   * @param extension extension of the file
   * @return a not existing file for the resource
   */
  private File getFileName(String extension) {
	  // write the image to a file
	  int counter = 1;
	  String filename;
	  File tempDir = new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
	  File destFile;
	  do{
		  filename = IMPORTED_IMAGE_PREFIX + counter + "." + extension; //$NON-NLS-1$
		  //destFile = parent.getFile(new Path(filename));
		  destFile = new File(tempDir, filename);
		  counter++;
	  } while (destFile.exists());
	  return destFile;
  }
 
  /**
   * Parse an SVG base64 image and convert it into a JR image. The extracted file is stored in the workspace
   * in the same folder of the report
   * 
   * @param imageElement the svg image element
   * @param previousTransform the current transform
   * @return a JR image
   */
  private JRDesignImage parseImage(SVGOMImageElement imageElement, AffineTransform previousTransform) {
	  AffineTransform newTransofrm = getTransofrm(imageElement);
	  previousTransform.concatenate(newTransofrm);
	  double x = parseCoordinates(imageElement.getAttribute("x")); //$NON-NLS-1$
	  double y = parseCoordinates(imageElement.getAttribute("y")); //$NON-NLS-1$
	  double width = Double.parseDouble(imageElement.getAttribute("width")); //$NON-NLS-1$
	  double height = Double.parseDouble(imageElement.getAttribute("height")); //$NON-NLS-1$
	  Rectangle2D rectFigure = new Rectangle2D.Double(x, y, width, height);
	  rectFigure = (Rectangle2D)previousTransform.createTransformedShape(rectFigure).getBounds2D();
	  JRDesignImage jrImage = new JRDesignImage(jConfig.getJasperDesign());
	  jrImage.setX((int)rectFigure.getX());
	  jrImage.setY((int)rectFigure.getY());
	  jrImage.setWidth((int)rectFigure.getWidth());
	  jrImage.setHeight((int)rectFigure.getHeight());
	  
	  String sourceData = imageElement.getAttributeNS("http://www.w3.org/1999/xlink", "href"); //$NON-NLS-1$ //$NON-NLS-2$
	  String[] splitData = sourceData.split(","); //$NON-NLS-1$
	  String extension;
	  if (splitData[0].contains("png")) { //$NON-NLS-1$
		  extension = "png"; //$NON-NLS-1$
	  } else if (splitData[0].contains("bmp")) { //$NON-NLS-1$
		  extension = "bmp"; //$NON-NLS-1$
	  } else if (splitData[0].contains("ico")) { //$NON-NLS-1$
		  extension = "ico"; //$NON-NLS-1$
	  } else {
		  extension = "jpg"; //$NON-NLS-1$
	  }
	 
	  // create a buffered image
	  BufferedImage image = decodeImage(imageElement, splitData[1].getBytes());
	  if (image != null) {
		  File destFile = getFileName(extension);
		  FileOutputStream outStream = null;
		  try {
			outStream = new FileOutputStream(destFile);
			ImageIO.write(image, extension, outStream);
		  } catch (Exception e) {
			e.printStackTrace();
		  } finally {
		    FileUtils.closeStream(outStream);
		  }
		  //The expression is set from the command returned by getResourceCreationCommands
		  resources.add(new Pair<File, JRDesignImage>(destFile, jrImage));
	  }
	  return jrImage;
  }
  
  /**
   * Parse an SVG ellipse element and convert it to a JR ellipse
   * 
   * @param ellipseElement the svg ellipse
   * @param previousTransform the current transform
   * @return the JR ellipse
   */
  private JRDesignEllipse parseEllipse(SVGOMEllipseElement ellipseElement, AffineTransform previousTransform) {
	  JRDesignEllipse result = new JRDesignEllipse(jConfig.getJasperDesign());
	  AffineTransform newTransofrm = getTransofrm(ellipseElement);
	  previousTransform.concatenate(newTransofrm);
	  double cx = Double.parseDouble(ellipseElement.getAttribute("cx")); //$NON-NLS-1$
	  double cy = Double.parseDouble(ellipseElement.getAttribute("cy")); //$NON-NLS-1$
	  double rx = Double.parseDouble(ellipseElement.getAttribute("rx")); //$NON-NLS-1$
	  double ry = Double.parseDouble(ellipseElement.getAttribute("ry")); //$NON-NLS-1$
	  Ellipse2D ellipseFigure = new Ellipse2D.Double(cx - rx , cy - ry,  rx * 2, ry * 2);
	  Rectangle2D rectFigure = (Rectangle2D)previousTransform.createTransformedShape(ellipseFigure).getBounds2D();
	  result.setX((int)rectFigure.getX());
	  result.setY((int)rectFigure.getY());
	  result.setWidth((int)rectFigure.getWidth());
	  result.setHeight((int)rectFigure.getHeight());
	  
	  CSSStyleDeclaration nodeStyle = ((SVGSVGElement)svgDocument.getDocumentElement()).getComputedStyle(ellipseElement, null);  
	  Color backgroundColor = getColor(nodeStyle, "fill"); //$NON-NLS-1$
	  if (backgroundColor != null) {
		  result.setBackcolor(backgroundColor);
	  }
	  
	  Float strokeWidth = getStrokeWidth(nodeStyle);
	  result.getLinePen().setLineWidth(strokeWidth);
	  Color strokeColor = getColor(nodeStyle, "stroke"); //$NON-NLS-1$
	  if (strokeColor != null) {
		  result.getLinePen().setLineColor(strokeColor);
	  }
	  
	  return result;
  }
  
  /**
   * Store an SVG document as a file and return it 
   * 
   * @param doc the document to save
   * @return the file of the saved document or null if something goes wrong
   */
  private File transcodeToSVG(Document doc) {
	  	File destFile = null;
	  	FileOutputStream outputStream = null;
	    try {
	        //Determine output type:
	        SVGTranscoder t = new SVGTranscoder();

	        //Set transcoder input/output
	        TranscoderInput input = new TranscoderInput(doc);
	        ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
	        OutputStreamWriter ostream = new OutputStreamWriter(bytestream, "UTF-8");
	        TranscoderOutput output = new TranscoderOutput(ostream);

	        //Perform transcoding
	        t.transcode(input, output);
	        ostream.flush();
	        ostream.close();
	        destFile = getFileName("svg");
	        byte[] svgBytes = bytestream.toByteArray();
	        outputStream = new FileOutputStream(destFile);
	        outputStream.write(svgBytes);
	    } catch (Exception e) {
	        e.printStackTrace();
	    } finally {
	    	FileUtils.closeStream(outputStream);
	    }
	    return destFile;
	}
  
  /**
   * Convert an {@link AffineTransform} to an SVG transformation matrix
   * 
   * @param at a not null {@link AffineTransform}
   * @return a string representing and SVG transformation matrix
   */
  public static String affineTransformToString(final AffineTransform at) {
      double[] matrix = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
      at.getMatrix(matrix);
      return matrixArrayToString(matrix);
  }
  public static String matrixArrayToString(double[] vals) {
      return new StringBuilder("matrix(").append(vals[0]).append(" ").append(vals[1]).append(" ").append(vals[2]).append(" ").append(vals[3]).append(" ").append(vals[4]).append(" ").append(vals[5]).append(") ").toString();
  }
  
  /**
   * Extract a path element from an SWG document and save it as a separate SVG to be used inside
   * and Image. The path is translated to start from the origin
   * 
   * @param pathElement the path element
   * @param previousTransform the {@link AffineTransform} of the path
   * @param awtShape the awt shape of the path
   * @return a not null list of {@link JRDesignElement}, that should contain only the image of the path
   */
  private List<JRDesignElement> exrtractPath(SVGOMPathElement pathElement, AffineTransform previousTransform, Shape awtShape){
	  List<JRDesignElement> result = new ArrayList<>();
	  
	  //calculate size and transformation
	  Rectangle2D bounds = awtShape.getBounds2D();
	  Number stroke = parseNumber(pathElement.getAttribute("stroke-width"));
	  if (stroke == null) {
		  stroke = 0d;
	  }
	  double x = bounds.getX();
	  double y = bounds.getY();
	  double width = bounds.getWidth();
	  double height = bounds.getHeight();
			  
	  Rectangle2D rectFigure = new Rectangle2D.Double(x, y, width, height);
	  rectFigure = (Rectangle2D)previousTransform.createTransformedShape(rectFigure).getBounds2D();
	  double imageWidth = rectFigure.getWidth()+stroke.doubleValue();
	  double imageHeight= rectFigure.getHeight()+ stroke.doubleValue();
	  //define the transformation that move the image into the origin
	  double yTranslation = bounds.getY()* previousTransform.getScaleY();
	  if (yTranslation < 0 && (bounds.getHeight()  * previousTransform.getScaleY())<0) {
		  //this will increase the y-offset since both are negative
		  yTranslation += bounds.getHeight()  * previousTransform.getScaleY();
	  }
	  double xTranslation = bounds.getX()* previousTransform.getScaleX();
	  if (xTranslation < 0 && (bounds.getWidth()  * previousTransform.getScaleX())<0) {
		  //this will increase the y-offset since both are negative
		  xTranslation += bounds.getWidth()  * previousTransform.getScaleX();
	  }
	  AffineTransform originTransform = new AffineTransform(previousTransform.getScaleX(), previousTransform.getShearY(), previousTransform.getShearX(), previousTransform.getScaleY(), 
				-xTranslation+stroke.doubleValue()/2, -yTranslation + stroke.doubleValue()/2);

	  //generate the document
	  DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();
	  String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
	  Document doc = impl.createDocument(svgNS, "svg", null);
	  Element svgRoot = doc.getDocumentElement();
	  svgRoot.setAttribute("width", String.valueOf(imageWidth));
	  svgRoot.setAttribute("height", String.valueOf(imageHeight));
	  Element group = (Element)doc.createElementNS("http://www.w3.org/2000/svg", "g");
	  svgRoot.appendChild(group);
	  Element copy = (Element)doc.importNode(pathElement, true);
	  group.setAttributeNS(null, SVGConstants.SVG_TRANSFORM_ATTRIBUTE, affineTransformToString(originTransform));
	  group.appendChild(copy);
	  File destFile = transcodeToSVG(doc);
	  
	  if (destFile != null) {
		  JRDesignImage jrImage = new JRDesignImage(jConfig.getJasperDesign());
		  jrImage.setX((int)rectFigure.getX());
		  jrImage.setY((int)rectFigure.getY());
		  jrImage.setWidth((int)imageWidth);
		  jrImage.setHeight((int)imageHeight);
		  resources.add(new Pair<File, JRDesignImage>(destFile, jrImage));
		  result.add(jrImage);
	  }
	  return result;
  }
  
  /**
   * Check if a path a {@link Shape} is a single line
   * 
   * @param awtShape a not null shape
   * @param transorm the affientransform of the same
   * @return true if the shape is a single line, false otherwise
   */ 
  private boolean isSingleLine(Shape awtShape, AffineTransform transorm) {
	  PathIterator iterator = awtShape.getPathIterator(transorm);
	  int lines = 0;
	  while(!iterator.isDone()) {
		  float[] coords = new float[6];
		  int segType = iterator.currentSegment(coords);
		  if (segType == PathIterator.SEG_CUBICTO || segType == PathIterator.SEG_QUADTO || segType == PathIterator.SEG_CLOSE) {
			  return false;
		  }
		  if (segType == PathIterator.SEG_LINETO) {
			  lines++;
			  if (lines > 1) {
				  return false;
			  }
		  }
		  iterator.next();
	  }
	  return true;
  }
  
  /**
   * Convert a path into a series of line
   * 
   * @param pathElement the path element
   * @param previousTransform the current transform 
   * @return the list of design element that represent the path
   */
  private List<JRDesignElement> parsePath(SVGOMPathElement pathElement, AffineTransform previousTransform) {
	  AffineTransform newTransofrm = getTransofrm(pathElement);
	  previousTransform.concatenate(newTransofrm);
	  AWTPathProducer pathProducer = new AWTPathProducer();
	  PathParser pathParser = new PathParser();
	  pathParser.setPathHandler(pathProducer);
      pathParser.parse(pathElement.getAttribute("d")); //$NON-NLS-1$
	  Shape awtShape = pathProducer.getShape();
	  if (isSingleLine(awtShape, previousTransform)) {
		  List<JRDesignElement> result = new ArrayList<>();
		  PathIterator iterator = awtShape.getPathIterator(previousTransform);
		  float[] lastCoords = null;
		  float[] firstCoords = null;
		  while(!iterator.isDone()) {
			  float[] coords = new float[6];
			  int segType = iterator.currentSegment(coords);
			  if (lastCoords != null) {
				  JRDesignLine line = new JRDesignLine();
				  double x1 = lastCoords[0];
				  double y1 = lastCoords[1];
				  double x2;
				  double y2;
				  if (segType == PathIterator.SEG_CLOSE) {
					  x2 = firstCoords[0];
					  y2 = firstCoords[1];
				  } else { 
					  x2 = coords[0];
					  y2 = coords[1];
				  }
				 int xLineStart;
				  int lineWdith;
				  LineDirectionEnum direction = null;
				  if (x1 < x2) {
					  xLineStart = (int)Math.round(x1);
					  lineWdith = (int)Math.round(x2 - x1);
				  } else {
					  xLineStart = (int)Math.round(x2);
					  lineWdith = (int)Math.round(x1 - x2);
				  }
				  int yLineStart;
				  int lineHeight;
				  if (y1 < y2) {
					  yLineStart = (int)Math.round(y1);
					  lineHeight = (int)Math.round(y2 - y1);
				  } else {
					  yLineStart = (int)Math.round(y2);
					  lineHeight = (int)Math.round(y1 - y2);
					  direction = LineDirectionEnum.BOTTOM_UP;
				  }
				  line.setX(xLineStart);
				  line.setY(yLineStart );
				  line.setWidth(lineWdith);
				  line.setHeight(lineHeight);
				  line.setDirection(direction);
				  result.add(line);
			  }
			  lastCoords = coords;
			  if (firstCoords == null) {
				  firstCoords = coords;
			  }
			  iterator.next();
		  }
		  return result;
	  } else return exrtractPath(pathElement, previousTransform, awtShape);
  }
  
  /**
   * Parse an SVG single element, case by case and invoke the method to convert it to the corresponding 
   * JR element
   * 
   * @param element the SVG element
   * @param previousTransform the current transformation 
   * @return a not null list of {@link JRDesignElement} created from this element
   */
  private List<JRDesignElement> parseElement(SVGOMElement element, AffineTransform previousTransform) {
	  List<JRDesignElement> result = new ArrayList<>();
	  if (element instanceof SVGOMTextElement) {
		  List<JRDesignStaticText> texts = parseText((SVGOMTextElement)element, new AffineTransform(previousTransform));
		  result.addAll(texts);
	  } else if (element instanceof SVGOMRectElement) {;
		  JRDesignRectangle rect = parseRectangle((SVGOMRectElement)element, new AffineTransform(previousTransform));
		  if (rect != null) {
			  result.add(rect);
		  }
	  } else if (element instanceof SVGOMGElement) {
		  result.addAll(parseGroup((SVGOMGElement)element, new AffineTransform(previousTransform)));
	  } else if (element instanceof SVGOMImageElement) {
		  SVGOMImageElement imageNode = (SVGOMImageElement)element;
		  JRDesignImage image = parseImage(imageNode, new AffineTransform(previousTransform));
		  if (image != null) {
			  result.add(image);
		  }
	  } else if (element instanceof SVGOMEllipseElement) {
		  SVGOMEllipseElement ellipseNode = (SVGOMEllipseElement)element;
		  JRDesignEllipse ellipse = parseEllipse(ellipseNode, new AffineTransform(previousTransform));
		  if (ellipse != null) {
			  result.add(ellipse);
		  }
	  } else if (element instanceof SVGOMPathElement) {
		  List<JRDesignElement> lines = parsePath((SVGOMPathElement)element, new AffineTransform(previousTransform));
		  result.addAll(lines);
	  } else{
		  System.out.println("SVG element " + element.toString() + " not supported");
		  JaspersoftStudioPlugin.getInstance().logWarning("SVG element " + element.toString() + " not supported");
	  }
	  return result;
  }
  
  /**
   * Parse the SVG group element 
   * 
   * @param group the group to parse
   * @param previousTransofrm the current transformation, must be not null
   * @return a not null list of {@link JRDesignElement} converted from the current of the group
   */
  public List<JRDesignElement> parseGroup(SVGOMGElement group, AffineTransform previousTransofrm) {
	  AffineTransform newTransform = getTransofrm(group);
	  previousTransofrm.concatenate(newTransform);
	  NodeList nodes = group.getChildNodes();
      List<JRDesignElement> result = new ArrayList<>();
	  for (int i = 0;  i < nodes.getLength();  i++) {
          Node node = nodes.item(i);
          if (node instanceof SVGOMElement) {
        	  result.addAll(parseElement((SVGOMElement)node, previousTransofrm));
          }
      }
	  return result;
  }
  
  /**
   * Explore all the svg for its content and convert it to a JasperReports equivalent format.
   * After the conversion the elements are moved to be on the left and top edge. If the svg
   * contains static images it is important to execute before the command returned from getResourceCreationCommands
   * This commands are delegated to create inside the workspace the resources of the static images, and they
   * also update the {@link JRDesignImage} with the correct name of the resource in the workspace
   * 
   * @return a not null list of {@link JRDesignElement}
   */
  public List<JRDesignElement> getJRElements() {
	  resources.clear();
      SVGOMSVGElement rootElement = getSVGDocumentRoot();
      NodeList nodes = rootElement.getChildNodes();
      AffineTransform startingStransform = new AffineTransform();
      List<JRDesignElement> result = new ArrayList<>();
      masks.clear();
      for (int i = 0;  i < nodes.getLength();  i++) {
          Node node = nodes.item(i);
          if (node instanceof SVGOMGElement) {
        	  result.addAll(parseElement((SVGOMGElement)node, startingStransform));
          } else if (node instanceof SVGOMPathElement) {
        	  //support for inline svg
        	  result.addAll(parseElement((SVGOMPathElement)node, startingStransform));
          }
      }
      //move the leftmost and topmost on the edge
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      for(JRDesignElement element : result) {
	    	  if (element.getX() < minX) {
	    		  minX = element.getX();
	    	  }
	    	  if (element.getY() < minY) {
	    		  minY = element.getY();
	    	  }
	      }
      for(JRDesignElement element : result) {
    	  	element.setX(element.getX() - minX);
    	  		element.setY(element.getY() - minY);
      }
      return result;
  }

  /**
   * Get the commands necessary to create in the workspace the static image resources necessary
   * for the static images found in the svg. Can be null if there are not resource to create
   * in the workspace, this commands can be undone
   * 
   * @return a command to create the resource or null
   */
  public Command getResourceCreationCommands() {
	  CompoundCommand result = new CompoundCommand();
	  for(Pair<File, JRDesignImage> resource : resources) {
		CreateResourceCommand resourceCommand = new CreateResourceCommand(resource.getKey(), resource.getValue(), jConfig);
		result.add(resourceCommand);
	  }
	  if (!result.isEmpty()) {
		  return result;
	  } else { 
		  return null;
	  }
  }

  /**
   * Returns an SVGOMSVGElement that is the document's root element.
   * 
   * @return The SVG document typecast into an SVGOMSVGElement.
   */
  private SVGOMSVGElement getSVGDocumentRoot() {
    return (SVGOMSVGElement)getSVGDocument().getDocumentElement();
  }

  /**
   * This will set the document to parse. This method also initializes
   * the SVG DOM enhancements, which are necessary to perform SVG and CSS
   * manipulations. The initialization is also required to extract information
   * from the SVG path elements.
   *
   * @param document The document that contains SVG content.
   */
  public void setSVGDocument( Document document ) {
    initSVGDOM( document );
    this.svgDocument = document;
  }

  /**
   * Returns the SVG document parsed upon instantiating this class.
   * 
   * @return A valid, parsed, non-null SVG document instance.
   */
  public Document getSVGDocument() {
    return this.svgDocument;
  }

  /**
   * Enhance the SVG DOM for the given document to provide CSS- and SVG-specific
   * DOM interfaces.
   * 
   * @param document The document to enhance.
   * @link http://wiki.apache.org/xmlgraphics-batik/BootSvgAndCssDom
   */
  private void initSVGDOM( Document document ) {
    UserAgent userAgent = new UserAgentAdapter();
    DocumentLoader loader = new DocumentLoader( userAgent );
    BridgeContext bridgeContext = new BridgeContext( userAgent, loader );
    bridgeContext.setDynamicState( BridgeContext.DYNAMIC );

    // Enable CSS- and SVG-specific enhancements.
    (new GVTBuilder()).build( bridgeContext, document );
  }
  
  /**
   * Use the SAXSVGDocumentFactory to parse the given URI into a DOM.
   * 
   * @param uri The path to the SVG file to read.
   * @return A Document instance that represents the SVG file.
   * @throws Exception The file could not be read.
   */
  private Document createSVGDocument( String uri ) throws IOException {
    String parser = XMLResourceDescriptor.getXMLParserClassName();
    SAXSVGDocumentFactory factory = new InkscapeSVGDocumentFactory( parser );
    return factory.createDocument( uri );
  }
}