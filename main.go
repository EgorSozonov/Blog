package main

import "fmt"
import "github.com/gin-gonic/gin"
import src "sozonov.tech/blog/src"
import "net/http"

func main() {
    src.Foo()
    fmt.Println("Hello, World!")
    var r = gin.Default()
    r.GET("/ping", func(c *gin.Context) {
        c.JSON(200, gin.H{
            "message": "pong",
        })
    })
    r.GET("/blog/*path", func(c *gin.Context) {
        c.String(http.StatusOK, c.Param("path")) 
    })
    r.Run(":10100")
}


