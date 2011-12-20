package org.jboss.forge.seam.persistence;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.FileNotFoundException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import javax.enterprise.context.ConversationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.Field;
import org.jboss.forge.parser.java.JavaAnnotation;
import org.jboss.forge.parser.java.JavaClass;
import org.jboss.forge.parser.xml.Node;
import org.jboss.forge.parser.xml.XMLParser;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.dependencies.Dependency;
import org.jboss.forge.project.dependencies.DependencyBuilder;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.project.facets.WebResourceFacet;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.shell.PromptType;
import org.jboss.forge.shell.ShellPrompt;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.forge.shell.plugins.SetupCommand;
import org.jboss.forge.spec.javaee.CDIFacet;
import org.jboss.forge.spec.javaee.PersistenceFacet;
import org.jboss.solder.core.ExtensionManaged;


@Alias("seam-persistence")
@RequiresFacet({PersistenceFacet.class, DependencyFacet.class, ResourceFacet.class, CDIFacet.class})
public class SeamPersistencePlugin implements Plugin
{
   @Inject Project project;
   @Inject ShellPrompt prompt;
   
   private static final String qualifierName = "Forge";


   @SetupCommand
   public void install(@Option(name = "enableDeclarativeTX", flagOnly = true) boolean enableDeclarativeTX,
                       @Option(name = "installManagedPersistenceContext", flagOnly = true) boolean installManagedPC)
   {
      installDependencies();

      if (enableDeclarativeTX)
      {
         setupDeclarativeTx();
      }

      if (installManagedPC)
      {
         installManagedPersistenceContext();
      }
   }

   @Command(value = "enable-declarative-tx")
   public void enableDeclarativeTx()
   {
      setupDeclarativeTx();
   }

   @Command(value = "install-managed-persistence-context")
   public void installManagedPerstistenceContextCommand()
   {
      installManagedPersistenceContext();
   }

   private void installManagedPersistenceContext()
   {
      JavaSourceFacet javaSourceFacet = project.getFacet(JavaSourceFacet.class);

      String packageName = prompt.promptCommon("What package do you want to use for the Persistence Context Procucer class?", PromptType.JAVA_PACKAGE, javaSourceFacet.getBasePackage());
      String className = prompt.prompt("How do you want to name the Persistence Context Procucer class?", String.class, "ManagedPersistenceContextFactory");

      JavaClass javaClass = JavaParser.create(JavaClass.class).setName(className).setPackage(packageName);
      Field<JavaClass> producerField = javaClass.addField("private EntityManagerFactory producerField");

      producerField.addAnnotation(ExtensionManaged.class);
      producerField.addAnnotation(Produces.class);
      producerField.addAnnotation(PersistenceUnit.class);
      producerField.addAnnotation(ConversationScoped.class);
      producerField.addAnnotation(qualifierName);

      javaClass.addImport(ConversationScoped.class);
      javaClass.addImport(Produces.class);
      javaClass.addImport(EntityManagerFactory.class);
      javaClass.addImport(PersistenceUnit.class);
      javaClass.addImport(ExtensionManaged.class);

      try
      {
         javaSourceFacet.saveJavaSource(javaClass);
         javaSourceFacet.saveJavaSource(createQualifier(packageName, qualifierName));
      } catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
   }
   
   /**
    * Creates a new Qualifier with the given package- and interface name
    * This could be extracted into the core to make it available to the core / other plugins
    * 
    * @author Daniel 'w0mbat' Sachse <sachsedaniel@googlemail.com>
    * @param packageName
    * @param interfaceName
    * @return new Qualifier
    */
  
   private JavaAnnotation createQualifier(String packageName, String interfaceName) {
       JavaAnnotation javaAnnotation = JavaParser.create(JavaAnnotation.class).setName(interfaceName).setPackage(packageName);
       
       javaAnnotation.addAnnotation(Qualifier.class);
       javaAnnotation.addAnnotation(Target.class).setEnumValue(TYPE, METHOD, PARAMETER, FIELD);
       javaAnnotation.addAnnotation(Retention.class).setEnumValue(RUNTIME);
       javaAnnotation.addAnnotation(Documented.class);

       return javaAnnotation;
   }

   private void setupDeclarativeTx()
   {
      FileResource<?> beansXml;

      if (project.hasFacet(WebResourceFacet.class))
      {
         WebResourceFacet webResourceFacet = project.getFacet(WebResourceFacet.class);
         beansXml = webResourceFacet.getWebResource("WEB-INF/beans.xml");
      } else {
         ResourceFacet resourceFacet = project.getFacet(ResourceFacet.class);
         beansXml = resourceFacet.getResource("META-INF/beans.xml");
      }

      Node node = XMLParser.parse(beansXml.getResourceInputStream());
      Node interceptors = node.getOrCreate("interceptors");
      List<Node> interceptorClasses = interceptors.get("class");
      boolean interceptorIsInstalled = false;
      for (Node interceptorClass : interceptorClasses)
      {
         if (interceptorClass.getText().equals("org.jboss.seam.transaction.TransactionInterceptor"))
         {
            interceptorIsInstalled = true;
            break;
         }
      }

      if (!interceptorIsInstalled)
      {
         interceptors.createChild("class").text("org.jboss.seam.transaction.TransactionInterceptor");
      }


      beansXml.setContents(XMLParser.toXMLInputStream(node));
   }

    private void installDependencies()
    {
        DependencyFacet dependencyFacet = project.getFacet(DependencyFacet.class);
        DependencyBuilder seamPersistenceDependency =
        DependencyBuilder.create()
        .setGroupId("org.jboss.seam.persistence")
        .setArtifactId("seam-persistence");
        
        if (!dependencyFacet.hasDirectDependency(seamPersistenceDependency))
        {
            if(!dependencyFacet.hasRepository(DependencyFacet.KnownRepository.JBOSS_NEXUS)) {
                dependencyFacet.addRepository(DependencyFacet.KnownRepository.JBOSS_NEXUS);
            }
            
            List<Dependency> versions = dependencyFacet.resolveAvailableVersions(seamPersistenceDependency);
            
            Dependency choosenVersion = prompt.promptChoiceTyped("Which version of Seam Persistence do you want to install?", versions, versions.get(versions.size() - 1));
            dependencyFacet.setProperty("seam.persistence.version", choosenVersion.getVersion());
            
            dependencyFacet.addDirectDependency(seamPersistenceDependency.setVersion("${seam.persistence.version}"));
        }
    }
}