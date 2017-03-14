package org.robolectric.res;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaxDocumentLoader extends DocumentLoader {
  private static final NodeHandler NO_OP_HANDLER = new NodeHandler();

  private final NodeHandler topLevelNodeHandler;
  private final XMLInputFactory factory;

  public StaxDocumentLoader(String packageName, FsFile resourceBase, StaxLoader... staxLoaders) {
    super(packageName, resourceBase);

    topLevelNodeHandler = new NodeHandler();
    for (StaxLoader staxLoader : staxLoaders) {
      staxLoader.addTo(topLevelNodeHandler);
    }

    factory = XMLInputFactory.newFactory();
  }

  @Override
  protected void loadResourceXmlFile(XmlContext xmlContext) {
    FsFile xmlFile = xmlContext.getXmlFile();

    XMLStreamReader xmlStreamReader = null;
    try {
      xmlStreamReader = factory.createXMLStreamReader(xmlFile.getInputStream());
      doParse(xmlStreamReader, xmlContext);
    } catch (Exception e) {
      throw new RuntimeException("error parsing " + xmlFile, e);
    }
    if (xmlStreamReader != null) {
      try {
        xmlStreamReader.close();
      } catch (XMLStreamException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected void doParse(XMLStreamReader reader, XmlContext xmlContext) throws XMLStreamException {
    NodeHandler nodeHandler = this.topLevelNodeHandler;
    Deque<NodeHandler> nodeHandlerStack = new ArrayDeque<>();

    while (reader.hasNext()) {
      int event = reader.next();
      switch (event) {
        case XMLStreamConstants.START_DOCUMENT:
          break;

        case XMLStreamConstants.START_ELEMENT:
          nodeHandlerStack.push(nodeHandler);
          NodeHandler elementHandler = nodeHandler.findMatchFor(reader);
          nodeHandler = elementHandler == null ? NO_OP_HANDLER : elementHandler;
          nodeHandler.onStart(reader, xmlContext);
          break;

        case XMLStreamConstants.CDATA:
        case XMLStreamConstants.CHARACTERS:
          nodeHandler.onCharacters(reader, xmlContext);
          break;

        case XMLStreamConstants.END_ELEMENT:
          nodeHandler.onEnd(reader, xmlContext);
          nodeHandler = nodeHandlerStack.pop();
          break;

        case XMLStreamConstants.ATTRIBUTE:
      }
    }
  }

  static class NodeHandler {
    private final String elementName;
    private final Map<String, String> attrs;
    private final Map<String, List<NodeHandler>> subHandlers = new HashMap<>();
    private NodeListener listener;

    NodeHandler(String elementName, Map<String, String> attrs) {
      this.elementName = "*".equals(elementName) ? null : elementName;
      this.attrs = attrs == null ? Collections.<String, String>emptyMap() : attrs;
    }

    NodeHandler() {
      this.elementName = null;
      this.attrs = Collections.emptyMap();
    }

    NodeHandler findMatchFor(XMLStreamReader xml) {
      String tagName = xml.getLocalName();
      List<NodeHandler> nodeHandlers = subHandlers.get(tagName);
      if (nodeHandlers == null) {
        nodeHandlers = subHandlers.get("*");
      }
      if (nodeHandlers != null) {
        for (NodeHandler subHandler : nodeHandlers) {
          if (subHandler.matches(xml)) {
            return subHandler;
          }
        }
      }

      return null;
    }

    NodeHandler findMatchFor(String elementName, Map<String, String> attrs) {
      List<NodeHandler> nodeHandlers = null;
      if (elementName != null) {
        nodeHandlers = subHandlers.get(elementName);
      }
      if (nodeHandlers == null) {
        nodeHandlers = subHandlers.get("*");
      }
      if (nodeHandlers != null) {
        for (NodeHandler subHandler : nodeHandlers) {
          if (subHandler.attrs.equals(attrs)) {
            return subHandler;
          }
        }
      }

      NodeHandler nodeHandler = new NodeHandler(elementName, attrs);
      String elementNameOrStar = elementName == null ? "*" : elementName;
      nodeHandlers = subHandlers.get(elementName);
      if (nodeHandlers == null) {
        nodeHandlers = new ArrayList<>();
        subHandlers.put(elementNameOrStar, nodeHandlers);
      }

      nodeHandlers.add(nodeHandler);
      return nodeHandler;
    }

    private boolean matches(XMLStreamReader xml) {
      if (elementName != null && !elementName.equals(xml.getLocalName())) {
        return false;
      }

      int attributeCount = xml.getAttributeCount();
      for (int i = 0; i < attributeCount; i++) {
        String attrName = xml.getAttributeLocalName(i);
        String expectedAttrValue = attrs.get(attrName);
        if (expectedAttrValue != null && !expectedAttrValue.equals(xml.getAttributeValue(i))) {
          return false;
        }
      }
      return true;
    }

    public NodeHandler addHandler(String elementName, Map<String, String> attrs) {
      return findMatchFor(elementName, attrs);
    }

    void addListener(NodeListener nodeListener) {
      if (listener != null) {
        throw new RuntimeException("already have a listener");
      }
      listener = nodeListener;
    }

    public void onStart(XMLStreamReader xml, XmlContext xmlContext) throws XMLStreamException {
      if (listener != null) listener.onStart(xml, xmlContext);
    }

    public void onCharacters(XMLStreamReader xml, XmlContext xmlContext) throws XMLStreamException {
      if (listener != null) listener.onCharacters(xml, xmlContext);
    }

    public void onEnd(XMLStreamReader xml, XmlContext xmlContext) throws XMLStreamException {
      if (listener != null) listener.onEnd(xml, xmlContext);
    }

    @Override
    public String toString() {
      return "/" + elementName + "[@" + attrs + "]";
    }
  }

  interface NodeListener {
    void onStart(XMLStreamReader xml, XmlContext xmlContext) throws XMLStreamException;
    void onCharacters(XMLStreamReader xml, XmlContext xmlContext) throws XMLStreamException;
    void onEnd(XMLStreamReader xml, XmlContext xmlContext) throws XMLStreamException;
  }
}