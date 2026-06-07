import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestBcrypt {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash1 = "$2a$10$SQA5lq7tQcU0S8PPPLeNFO3LyNB3ClzJGrkwkUnImQRNXz4CRAawS";
        String password = "Tkdenddlwkfl2@";
        System.out.println("Match? " + encoder.matches(password, hash1));
        
        String hash2 = "$2a$10$Pio9bB253kRqJ.JyPJ0vUu3CpbPO.ZlmMpCgw3d8AzllU89Q4sZYa"; // for stars_ka@naver.com
        System.out.println("Match 2? " + encoder.matches(password, hash2));
        
        String hash3 = "$2a$10$D8IUDmRCKnlGbCPEEiL16eJWnGCl4Y5kYfwNnc5pVVme1WsQRuH12"; // newuser2
        System.out.println("Match 3? " + encoder.matches(password, hash3));
    }
}
