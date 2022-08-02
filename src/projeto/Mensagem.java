package projeto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import projeto.*;
/*
    Vitor Medeiros : 11201720112

    Observacoes:
        - Todas as requisicoes UDP fazem uso do metodo  socket.setSoTimeout(TIMEOUT_MILISECONDS), onde TIMEOUT_MILISECONDS
        é uma constante definida na classe. Em caso de timeout, o bloco catch da excecao chama novamente o metodo em questao
        para tentar novamente a comunicacao com o servidor. Secao 5.g

        - Infelizmente quando comecei o projeto eu nao tinha entendido exatamente o proposito da classe mensagem e que
        ela deveria atuar como um object de comunicacao entre servidor e cliente. Utilizei a classe mensagem como uma
        classe Singleton que mantem o estado dos arquivos que estao em determinado host, assim como os hosts que possuem
        determinado arquivo, as informacoes sao mantidas em um ConcurrentHashmap(). Apesar de nao ter utilizado a classe
        de acordo com as especificacoes, espero que seja possível considerar a execucao da aplicacao como um tod o que está
        funcionando e atende aos outros critérios de avaliacao.

 */

public class Mensagem {

    private static Mensagem single_instance = null;

    //port number and list of file names available
    public  Map<String,List<String>> portToFiles;
    //file name and list of hosts
    public Map<String,List<String>> filesToPort;

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

    public void updatePortToFiles(String host , String file) {

        List<String> original = portToFiles.get(host);

        if(!original.contains(file)){
            original.add(file);
        }
        portToFiles.put(host,original);
    }

   /*
   Quando o server recebe um LEAVE ou quando a requisicao ALIVE nao recebe resposta
    */
    public void removeHost (String host){
        portToFiles.remove(host);
        List<String> updatedHosts = new ArrayList<>();

        for (Iterator<Map.Entry<String, List<String>>> iter = filesToPort.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<String, List<String>> entry = iter.next();
            String file = entry.getKey();
            List<String> hosts = entry.getValue();

            if (entry.getValue().contains(host)){
                for(String Listhost : hosts){
                    if (!Listhost.equals(host)){
                        updatedHosts.add(Listhost);
                    }
                }

                filesToPort.remove(file);

                if(updatedHosts.size() > 0) {
                    filesToPort.put(file, updatedHosts);
                }
                updatedHosts = new ArrayList<>();
            }



        }
    }

    public List<String> getFilesToPort(String fileName) {
        List<String> lista = filesToPort.get(fileName.trim());
        if(lista == null){
            return new ArrayList<String>();
        } else{
            return lista;
        }
    }

    public void updateFilesToPort(String host , String file) {
        List<String> hostsWithFiles = getFilesToPort(file);
        if(!hostsWithFiles.contains(host)){
            hostsWithFiles.add(host);
        }
        setFilesToPort(file.trim(),hostsWithFiles);
    }

    public void setFilesToPort(String fileName, List<String> hosts) {
        filesToPort.put(fileName,hosts);
    }


}
