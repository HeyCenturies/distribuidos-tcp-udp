package tcpdemo;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Mensagem {

    private static Mensagem single_instance = null;

    //port number and list of file names available
    private Map<String,List<String>> portToFiles;
    //file name and list of hosts
    private Map<String,List<String>> filesToPort;

    private Mensagem()
    {
        portToFiles = new ConcurrentHashMap<String,List<String>>();
        filesToPort = new ConcurrentHashMap<String,List<String>>();
    }

    public static Mensagem getInstance()
    {
        if (single_instance == null)
            single_instance = new Mensagem();

        return single_instance;
    }

    public Map<String, List<String>> getPortToFiles() {
        return portToFiles;
    }

    public void setPortToFiles(String host , List<String> files) {
        portToFiles.put(host,files);
    }

    public void removeHost (String host){
        portToFiles.remove(host);
        List<String> updatedHosts = new ArrayList<>();

        for (Iterator<Map.Entry<String, List<String>>> iter = filesToPort.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<String, List<String>> entry = iter.next();
            String file = entry.getKey();
            List<String> hosts = entry.getValue();
            if (entry.getValue().contains(host))
                for(String Listhost : hosts){
                    if (!Listhost.equals(host)){
                        updatedHosts.add(Listhost);
                    }
                }
            filesToPort.put(file,updatedHosts);
        }
    }

    public List<String> getFilesToPort(String fileName) {
        List<String> lista = filesToPort.get(fileName);
        if(lista == null){
            return new ArrayList<String>();
        } else{
            return lista;
        }
    }

    public void setFilesToPort(String fileName, List<String> hosts) {
        filesToPort.put(fileName,hosts);
    }


}
