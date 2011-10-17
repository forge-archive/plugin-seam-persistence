package org.jboss.forge.seam.persistence;

import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.Field;
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
import org.jboss.forge.shell.plugins.*;
import org.jboss.forge.spec.javaee.CDIFacet;
import org.jboss.forge.spec.javaee.PersistenceFacet;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.util.List;

@Alias("seam-persistence")
@RequiresFacet({PersistenceFacet.class, DependencyFacet.class, ResourceFacet.class, CDIFacet.class})
public class SeamPersistencePlugin implements Plugin
{
   @Inject Project project;
   @Inject ShellPrompt prompt;


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

      producerField.addAnnotation("ExtensionManaged");
      producerField.addAnnotation("Produces");
      producerField.addAnnotation("PersistenceUnit");
      producerField.addAnnotation("ConversationScoped");

      javaClass.addImport("javax.enterprise.context.ConversationScoped");
      javaClass.addImport("javax.enterprise.inject.Produces");
      javaClass.addImport("javax.persistence.EntityManagerFactory");
      javaClass.addImport("javax.persistence.PersistenceUnit");
      javaClass.addImport("org.jboss.solder.core.ExtensionManaged");

      try
      {
         javaSourceFacet.saveJavaSource(javaClass);
      } catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
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

      if (!dependencyFacet.hasDependency(seamPersistenceDependency))
      {
         if(!dependencyFacet.hasRepository(DependencyFacet.KnownRepository.JBOSS_NEXUS)) {
            dependencyFacet.addRepository(DependencyFacet.KnownRepository.JBOSS_NEXUS);
         }

         List<Dependency> versions = dependencyFacet.resolveAvailableVersions(seamPersistenceDependency);

         Dependency choosenVersion = prompt.promptChoiceTyped("Which version of Seam Persistence do you want to install?", versions, versions.get(versions.size() - 1));
         dependencyFacet.setProperty("seam.persistence.version", choosenVersion.getVersion());

         dependencyFacet.addDependency(seamPersistenceDependency.setVersion("${seam.persistence.version}"));
      }
   }
}
