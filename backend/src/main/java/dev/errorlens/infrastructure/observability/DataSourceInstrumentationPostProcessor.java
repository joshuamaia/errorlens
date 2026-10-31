package dev.errorlens.infrastructure.observability;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * Envolve o DataSource em um proxy JDBC (datasource-proxy) para medir comandos SQL reais.
 */
@Component
public class DataSourceInstrumentationPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
            SqlInstrumentationListener listener = new SqlInstrumentationListener();
            return ProxyDataSourceBuilder.create(dataSource)
                    .name("errorlens-instrumented")
                    .listener(listener)
                    .methodListener(listener)
                    .build();
        }
        return bean;
    }
}
