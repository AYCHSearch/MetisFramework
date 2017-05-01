/*
 * Copyright 2007-2013 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */
package eu.europeana.identifier.service;

import eu.europeana.identifier.service.utils.Decoupler;
import eu.europeana.identifier.service.utils.IdentifierNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by ymamakis on 7/14/16.
 */
@Configuration
public class Application {
    @Bean
    IdentifierService getIdentifierService(){
        return new IdentifierService();
    }

    @Bean
    ItemizationService getItemizationService(){
        return new ItemizationService();
    }

    @Bean
    IdentifierNormalizer getIdentifierNormalizer(){
        return new IdentifierNormalizer();
    }

    @Bean
    Decoupler getDecoupler(){
        return new Decoupler();
    }
}