package uk.co.bithatch.opensim.jlib;

public class ConsoleTest {

	public static void main(String[] args) {
		try(var console = new OpensimRESTConsole("http://localhost:9000", "ConsoleUser", "ConsolePass")) {
			for(var line : console.execute("estate show").toList()) {
				System.out.println(line);
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
