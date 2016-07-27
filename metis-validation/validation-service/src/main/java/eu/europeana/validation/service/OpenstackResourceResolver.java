package eu.europeana.validation.service;

import org.apache.log4j.Logger;
import org.w3c.dom.ls.LSInput;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Class enabling classpath XSD reading for split XSDs. This is because of an issue with JAXP XSD loading
 * Created by ymamakis on 12/21/15.
 */
public class OpenstackResourceResolver implements AbstractLSResourceResolver {
    private String prefix;
    private static final Logger logger =  Logger.getRootLogger();
    private static Map<String,InputStream> cache;
    public void setProvider(SwiftProvider provider) {
        this.provider = provider;
    }

    private SwiftProvider provider;
    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        try {
            if(cache==null){
                cache= new HashMap<>();
            }
            LSInput input = new ClasspathLSInput();
            InputStream stream;
            if(!systemId.startsWith("http")) {

                stream = provider.getObjectApi().get(prefix+"/"+systemId).getPayload().openStream();

            }else {
                if(cache.get(systemId)==null) {
                    stream = this.getClass().getClassLoader().getResourceAsStream("xml.xsd");
                    cache.put(systemId,stream);
                } else {
                    stream = cache.get(systemId);
                }
            }
            input.setPublicId(publicId);
            input.setSystemId(systemId);
            input.setBaseURI(baseURI);
            input.setCharacterStream(new InputStreamReader(stream));

            return input;
        } catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage());
        } return null;
    }
    /**
     * @return the prefix
     */
    public String getPrefix() {
        return prefix;
    }

    @Override
    public SwiftProvider getSwiftProvider() {
        return provider;
    }

    /**
     * @param prefix the prefix to set
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}

