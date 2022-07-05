package projeto;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ThreadAtendimento extends Thread{
    
    private Socket no = null;
    public ThreadAtendimento(Socket node){
        this.no = node;
    }
    
    public void run() {
        try{

            InputStreamReader isr = new InputStreamReader(no.getInputStream());
            BufferedReader reader = new BufferedReader(isr);

            OutputStream os = no.getOutputStream();
            DataOutputStream writer = new DataOutputStream(os);

            //pega nome do arquivo
            String texto = reader.readLine();


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
