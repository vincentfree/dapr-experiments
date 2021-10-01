pub mod helloClient;


fn client() -> String {
    let client = reqwest::Client::New();
}

pub fn call_hello_service() -> String {
    let endpoint = "http://localhost:3500/v1.0/invoke/order-backend/method/hello?failure=true";
    reqwest::get(endpoint).await?;
    
    println!("");
}
