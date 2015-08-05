package org.jboss.resteasy.test.regression;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.ext.ContextResolver;
import java.lang.annotation.Annotation;
import java.sql.Date;

import static org.junit.Assert.assertNotNull;
/**
 * resteasy-584
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ResteasyProviderFactoryTest
{
	private ResteasyProviderFactory factory;

	@Before
	public void createBean() {
		factory = new ResteasyProviderFactory();
	}

	@Test
	public void shouldReturnStringParameterUnmarshallerAddedForType() {
		factory.addStringParameterUnmarshaller(MyStringParameterUnmarshaller.class);

		assertNotNull(factory.createStringParameterUnmarshaller(Date.class));
	}

	public static class MyStringParameterUnmarshaller implements StringParameterUnmarshaller<Date>
   {

		@Override
		public void setAnnotations(Annotation[] annotations) {
	}

		@Override
		public Date fromString(String str) {
			return null;
		}

	}

   @Test
   public void testRegisterProvider() throws Exception
   {
      ResteasyProviderFactory factory = new ResteasyProviderFactory();
      factory.register(new ContextResolver<String>()
      {

         @Override
         public String getContext(Class<?> type)
         {
            return "foo bar";

         }

      });
   }

   @Test
   public void testRegisterProviderAsLambda() throws Exception
   {
      ResteasyProviderFactory factory = new ResteasyProviderFactory();
      factory.register((ContextResolver<String>) type -> "foo bar");
   }
}
