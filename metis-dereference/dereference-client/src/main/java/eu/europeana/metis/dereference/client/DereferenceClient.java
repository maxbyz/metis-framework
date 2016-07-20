package eu.europeana.metis.dereference.client;

import eu.europeana.metis.dereference.Vocabulary;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * A REST wrapper client to be used for dereferencing
 *
 * Created by ymamakis on 2/15/16.
 */
public class DereferenceClient {
    private RestTemplate restTemplate;
    private String hostUrl;
    public DereferenceClient(){
        restTemplate = new RestTemplate();
        Properties props = new Properties();
        try {
            props.load(this.getClass().getClassLoader().getResourceAsStream("client.properties"));
            hostUrl = props.getProperty("host.url");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * Create a vocabulary
     * @param voc The vocabulary to persist
     */
    public void createVocabulary(Vocabulary voc){
        restTemplate.postForObject(hostUrl+"/vocabulary",voc,Void.class);
    }

    /**
     * Update a vocabulary
     * @param voc The vocabulary to update
     */
    public void updateVocabulary(Vocabulary voc){
        try {
            restTemplate.put(new URI(hostUrl+"/vocabulary"), voc);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete a vocabulary
     * @param voc The vocabulary to delete
     */
    public void deleteVocabulary(Vocabulary voc){
        restTemplate.delete(hostUrl+"/vocabulary",voc);
    }

    /**
     * Retrieve the vocabulary by name
     * @param name The name of the vocabulary to retrieve
     * @return The retrieved vocabulary
     */
    public Vocabulary getVocabularyByName(String name){
        Vocabulary voc = restTemplate.getForObject(hostUrl+"/vocabulary/"+name,Vocabulary.class);
        return voc;
    }

    /**
     * Retrieve all the vocabularies
     * @return The list of all vocabularies
     */
    public List<Vocabulary> getAllVocabularies(){
        List<Vocabulary> vocs = (List<Vocabulary>)restTemplate.getForObject(hostUrl+"/vocabularies",List.class);
        return vocs;
    }

    /**
     * Delete an entity by URL
     * @param uri The url of the entity
     */
    public void deleteEntity(String uri){
        restTemplate.delete(hostUrl+"/entity/"+URLEncoder.encode(uri));
    }

    /**
     * Update Entity by URL
     * @param uri The url of the Entity
     * @param xml The xml to update the entity with
     */
    public void updateEntity(String uri, String xml){
        Map<String,String> params = new HashMap<>();
        params.put("uri", uri);
        params.put("xml",xml);
        restTemplate.postForEntity(hostUrl+"/entity",params,null);
    }

    /** Dereference an entity
     * @param uri the uri to dereference
     * @return A string of the referenced response
     */
    public String dereference(String uri){
        String uriString = null;
        try {
            uriString = URLEncoder.encode(uri,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return restTemplate.getForObject(hostUrl+"/dereference?uri="+ uriString,String.class);
    }
}