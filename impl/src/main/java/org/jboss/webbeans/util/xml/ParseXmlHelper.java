package org.jboss.webbeans.util.xml;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.inject.DefinitionException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;

import org.dom4j.Element;
import org.jboss.webbeans.CurrentManager;
import org.jboss.webbeans.ManagerImpl;
import org.jboss.webbeans.introspector.AnnotatedItem;
import org.jboss.webbeans.introspector.jlr.AnnotatedClassImpl;
import org.jboss.webbeans.resources.DefaultResourceLoader;
import org.jboss.webbeans.resources.spi.ResourceLoader;

public class ParseXmlHelper
{
   private static List<AnnotatedItemReceiver> receivers;
   
   private static ResourceLoader resourceLoader;

   static
   {
      receivers = initializeReceivers();
      resourceLoader = new DefaultResourceLoader();
   }

   public static Set<AnnotatedItem<?, ?>> getBeanItems(List<Element> beans)
   {
      Set<AnnotatedItem<?, ?>> result = new HashSet<AnnotatedItem<?, ?>>();

      for (Element bean : beans)
         result.add(receiveBeanItem(bean));

      return result;
   }

   private static AnnotatedItem<?, ?> receiveBeanItem(Element element)
   {
      for (AnnotatedItemReceiver receiver : receivers)
      {
         if (receiver.accept(element))
         {
            return receiver.receiveAnnotatedItem(element);
         }
      }

      throw new DefinitionException("definition of a bean " + element.getName() + " is incorrect");
   }

   private static List<AnnotatedItemReceiver> initializeReceivers()
   {
      List<AnnotatedItemReceiver> receivers = new ArrayList<AnnotatedItemReceiver>();

      AnnotatedItemReceiver jmsResourceReceiver = new AnnotatedItemReceiver()
      {
         public boolean accept(Element element)
         {
            return isJMSResource(element);
         }

         public AnnotatedItem<?, ?> receiveAnnotatedItem(Element element)
         {
            return receiveJMSResourceItem(element);
         }

      };
      AnnotatedItemReceiver resourceReceiver = new AnnotatedItemReceiver()
      {
         public boolean accept(Element element)
         {
            return isResource(element);
         }

         public AnnotatedItem<?, ?> receiveAnnotatedItem(Element element)
         {
            return receiveResourceItem(element);
         }

      };
      AnnotatedItemReceiver sessionBeanReceiver = new AnnotatedItemReceiver()
      {
         public boolean accept(Element element)
         {
            return isSessionBean(element);
         }

         public AnnotatedItem<?, ?> receiveAnnotatedItem(Element element)
         {
            return receiveSessionBeanItem(element);
         }

      };
      AnnotatedItemReceiver simpleBeanReceiver = new AnnotatedItemReceiver()
      {
         public boolean accept(Element element)
         {
            return isSimpleBean(element);
         }

         public AnnotatedItem<?, ?> receiveAnnotatedItem(Element element)
         {
            return receiveSimpleBeanItem(element);
         }

      };
      
      //order of elements is important
      receivers.add(jmsResourceReceiver);
      receivers.add(resourceReceiver);
      receivers.add(sessionBeanReceiver);
      receivers.add(simpleBeanReceiver);

      return receivers;
   }
   
   private static boolean isJMSResource(Element element)
   {
      if (isJavaEeNamespace(element) && 
            (element.getName().equalsIgnoreCase(XmlConstants.TOPIC) || 
                  element.getName().equalsIgnoreCase(XmlConstants.QUEUE)))
         return true;
      return false;
   }

   private static AnnotatedItem<?, ?> receiveJMSResourceItem(Element element)
   {
      final Element jmsElement = element;
      
      if(jmsElement.getName().equalsIgnoreCase(XmlConstants.QUEUE))
      {
         Queue queue = new Queue()
         {
            public String getQueueName() throws JMSException
            {
               return getJmsResourceName(jmsElement);
            }
         };
         
         return AnnotatedClassImpl.of(queue.getClass());
      }
                  
      Topic topic = new Topic()
      {
         public String getTopicName() throws JMSException
         {
            return getJmsResourceName(jmsElement);
         }         
      };
      
      return AnnotatedClassImpl.of(topic.getClass());
   }
   
   private static boolean isResource(Element element)
   {
      Iterator<?> elIterator = element.elementIterator();
      while (elIterator.hasNext())
      {
         Element child = (Element) elIterator.next();
         if (isJavaEeNamespace(child) && 
               (child.getName().equalsIgnoreCase(XmlConstants.RESOURCE) || 
                     child.getName().equalsIgnoreCase(XmlConstants.PERSISTENCE_CONTEXT) || 
                     child.getName().equalsIgnoreCase(XmlConstants.PERSISTENCE_UNIT) || 
                     child.getName().equalsIgnoreCase(XmlConstants.EJB) || 
                     child.getName().equalsIgnoreCase(XmlConstants.WEB_SERVICE_REF)))
            return true;
      }
      return false;
   }

   private static AnnotatedItem<?, ?> receiveResourceItem(Element element)
   {
      // TODO:
      return null;
   }
   
   private static boolean isSessionBean(Element element)
   {
      ManagerImpl manager = CurrentManager.rootManager();
      if (manager.getEjbDescriptorCache().containsKey(element.getName()) ||
            element.attribute(XmlConstants.EJB_NAME) != null)
         return true;
      return false;
   }

   private static AnnotatedItem<?, ?> receiveSessionBeanItem(Element element)
   {
      // TODO:
      return null;
   }
   
   private static boolean isSimpleBean(Element element)
   {
      Class<?> beanClass = loadClass(element);

      if (!Modifier.isAbstract(beanClass.getModifiers()) && 
            beanClass.getTypeParameters().length == 0)
         return true;

      return false;
   }

   private static AnnotatedItem<?, ?> receiveSimpleBeanItem(Element element)
   {
      Class<?> beanClass = loadClass(element);

      if (!Modifier.isStatic(beanClass.getModifiers()) && 
            beanClass.isMemberClass())
         throw new DefinitionException("class " + beanClass + " is a non-static inner class");

      // if (beanClass.getTypeParameters().length > 0)
      // throw new DefinitionException("class " + beanClass +
      // " is a parameterized type");

      // TODO:
      // boolean isDecorator = false;
      // if (Modifier.isAbstract(beanClass.getModifiers()) && !isDecorator)
      // throw new DefinitionException("class " + beanClass +
      // " is an abstract and not Decorator");

      return AnnotatedClassImpl.of(beanClass);
   }

   private static Class<?> loadClass(Element element)
   {
      String beanUri = element.getNamespace().getURI();
      String packageName = beanUri.replaceFirst(XmlConstants.URN_PREFIX, "");
      String classPath = packageName + "." + element.getName();
      return resourceLoader.classForName(classPath);
   }
   
   private static String getJmsResourceName(Element element)
   {
      Iterator<?> elIterator = element.elementIterator();
      while (elIterator.hasNext())
      {
         Element child = (Element) elIterator.next();
         if (isJavaEeNamespace(child) && 
               child.getName().equalsIgnoreCase(XmlConstants.RESOURCE))
         {
            Iterator<?> chIterator = child.elementIterator();
            while(chIterator.hasNext())
            {
               Element chChild = (Element) chIterator.next();
               if (isJavaEeNamespace(chChild) && 
                     (chChild.getName().equalsIgnoreCase(XmlConstants.NAME) || 
                           chChild.getName().equalsIgnoreCase(XmlConstants.MAPPED_NAME)))
               {
                  return chChild.getName();
               }
            }
         }         
      }
      throw new DefinitionException("Incorrect JMSResource declaration for " + element.getName());
   }
   
   public static boolean isJavaEeNamespace(Element element)
   {
      return element.getNamespace().getURI().equalsIgnoreCase(XmlConstants.JAVA_EE_NAMESPACE);
   }
}
