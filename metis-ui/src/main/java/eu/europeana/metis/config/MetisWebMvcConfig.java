package eu.europeana.metis.config;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import com.github.jknack.handlebars.Handlebars;
import com.github.mjeanroy.springmvc.view.mustache.MustacheCompiler;
import com.github.mjeanroy.springmvc.view.mustache.MustacheTemplateLoader;
import com.github.mjeanroy.springmvc.view.mustache.MustacheViewResolver;
import com.github.mjeanroy.springmvc.view.mustache.core.DefaultTemplateLoader;
import com.github.mjeanroy.springmvc.view.mustache.handlebars.HandlebarsCompiler;

import eu.europeana.metis.controller.MetisPageController;
import eu.europeana.metis.controller.MetisUserPageController;

/**
 * Web MVC configuration of Metis web application.
 * Handlebars is taken as a Java framework for rendering Mustache templates.
 * @author alena
 *
 */
@Configuration
@EnableWebMvc
public class MetisWebMvcConfig extends WebMvcConfigurerAdapter {
	@Override
	public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
		configurer.enable();	
	}
	
	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		StringHttpMessageConverter converter = new StringHttpMessageConverter();
		converter.setSupportedMediaTypes(Arrays.asList(new MediaType("text", "plain", Charset.forName("UTF-8"))));
		converters.add(converter);
		super.configureMessageConverters(converters);
	}
	
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@Bean
	public MetisPageController pandoraPageController(){
		return new MetisPageController();
	}

	@Bean
	public MetisUserPageController userPageController(){
		return new MetisUserPageController();
	}
	
	@Bean
	public Handlebars handlebars() {
		return new Handlebars();
	}
	
	@Bean 
	public MustacheTemplateLoader mustacheTemplateLoader(ResourceLoader resourceLoader){
		MustacheTemplateLoader loader =  new DefaultTemplateLoader(resourceLoader);
		loader.setPrefix("WEB-INF/app/views/");
		loader.setSuffix(".mustache");
		return loader;
	}	
	
	@Bean
	public MustacheCompiler mustacheCompiler(Handlebars handlebars,MustacheTemplateLoader loader){
		return new HandlebarsCompiler(handlebars,loader);
	}
	
	@Bean  
    public ViewResolver getViewResolver(ResourceLoader resourceLoader, MustacheCompiler mustacheCompiler) {
		MustacheViewResolver mustacheViewResolver = new MustacheViewResolver(mustacheCompiler);
//		mustacheViewResolver.setCharacterEncoding("UTF-8");
		mustacheViewResolver.setContentType("text/html; charset=UTF-8");
		return mustacheViewResolver;
    }
}