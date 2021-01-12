package gRPC_NumberMultiplierService;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class GrpcServer {
	
	// Informazioni di configurazione del server
	private final int port;
	private final Server server;
	   
	// Costruzione del server
	public GrpcServer(int port) {
		this(ServerBuilder.forPort(port), port);
	}
	   
	
	   public GrpcServer(ServerBuilder<?> serverBuilder, int port) {
	      this.port = port;
	      ArrayList<AddressBook> addressBooks = new ArrayList();
	      server = serverBuilder.addService(new AddressGuideService(addressBooks))
	                   .build();
	   }

	   
	public static void main(String[] args) throws IOException, InterruptedException {
		GrpcServer server = new GrpcServer(8980);
	    server.start();
	    server.blockUntilShutdown();
	}
	   
	   private void blockUntilShutdown() throws InterruptedException {
	      if (server != null) {
	         server.awaitTermination();
	      }
	   }
	   
	   private void start() throws IOException {
	      server.start();
	      logger.info("Server started, listening on " + port);
	  Runtime.getRuntime().addShutdownHook(new Thread() {
	     @Override
	     public void run() {
	        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
	System.err.println("*** shutting down gRPC server since JVM is shutting down");
	try {
	   GrpcServer.this.stop();
	} catch (InterruptedException e) {
	   e.printStackTrace(System.err);
	}
	System.err.println("*** server shut down");
	         }
	      });
	   }
	   
	   private void stop() throws InterruptedException {
	      if (server != null) {
	         server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
	      }
	   }
	   
	   private static class AddressGuideService extends AddressGuideGrpc.AddressGuideImplBase {
	      private final Collection<AddressBook> addressbookCollection;
	      
	      public AddressGuideService(Collection<AddressBook> addressBookCollection) {
	         this.addressbookCollection = addressBookCollection;
	      }
	      
	      @Override
	      public void getAddressBook(Person request, StreamObserver<AddressBook> responseObserver) {
	         responseObserver.onNext(getAddressBook());
	         responseObserver.onCompleted();
	      }
	      
	      private AddressBook getAddressBook() {
	         return AddressBook.newBuilder().addPeople(Person.newBuilder().build()).build();
	      }
	   }
}