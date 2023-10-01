use axum::{
    routing::{get, post},
    http::StatusCode,
    response::IntoResponse,
    Json, Router,
    extract::Path 
};
use serde::{Deserialize, Serialize};
use std::net::SocketAddr;


#[tokio::main]
async fn main() {
    // initialize tracing
    tracing_subscriber::fmt::init();

    // build our application with a route
    let app = Router::new()
        .route("/", get(root))
        .route("/foo/*wild", get(wildH))
        .route("/users", post(createUser));

    // run our app with hyper
    // `axum::Server` is a re-export of `hyper::Server`
    let addr = SocketAddr::from(([127, 0, 0, 1], 3000));
    tracing::debug!("listening on {}", addr);
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();
}

async fn wildH(Path(wild): Path<String>) -> String {
    wild
}

async fn root() -> &'static str {
    "Hello, World!"
}

async fn createUser(
    Json(payload): Json<CreateUser>,
) -> (StatusCode, Json<User>) {
    // this argument tells axum to parse the request body
    // as JSON into a `CreateUser` type
    let user = User {
        id: 1337,
        username: payload.username,
    };

    // this will be converted into a JSON response
    // with a status code of `201 Created`
    (StatusCode::CREATED, Json(user))
}

#[derive(Deserialize)]
struct CreateUser {
    username: String,
}

#[derive(Serialize)]
struct User {
    id: u64,
    username: String,
}

