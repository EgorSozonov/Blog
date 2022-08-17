package tech.sozonov.blog.templates


object BlogTemplate {
    val template0 = """<!DOCTYPE html>
    <html>
    <head>"""


    val templateHeadCloseBodyStart = """
    </head>
    <body>
        <div class="__wrapper">
            <div class="__navbar" id="__theNavBar">
                <div class="__menuTop">
                    <div class="__svgButton" style="text-align: center;" 
                    title="Temporal sorting">                   
                        <a onclick='reorderTemporal(); return false;'> 
                            <svg id="__sorterTemp" class="__swell" width="20" height="20" 
                                viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 35 20 h 30 c 0 30 -30 30 -30 60 h 30 c 0 -30 -30 -30 -30 -60" />
                            </svg>
                        </a>                        
                    </div>
                    <div style="text-align: center;">                    
                        <a href="http://sozonov.tech" title="Home page">
                            <svg id="__homeIcon" class="__swell" width="20" height="20" 
                                viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 30 45 h 40 v 25 h -40 v -25 " />
                                <path d="M 22 50 l 28 -25 l 28 25" />
                            </svg>
                        </a>
                    </div>
                    <div class="__svgButton" style="text-align: center;">                    
                        <a onclick='reorderThematic(); return false;' title="Nominal sorting">
                            <svg class="__swell __sortingBorder" id="__sorterNom" width="20" 
                            height="20"
                             viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 35 80 v -60 l 30 60 v -60" />
                            </svg>
                    </a>
                    </div>
                </div>
                <div class="__menu" id="__theMenu"></div>
            </div>

            <div class="__divider" id="__divider" onClick="toggleNavBar();">&lt;</div>
            <div class="__menuToggler __hidden" id="__menuToggler">
                <div class="__svgButton" style="text-align: center;" title="Temporal sorting">                   
                        <a onclick="toggleNavBar(); return false;"> 
                            <svg class="__swell" width="20" height="20" viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50"></circle>
                                <path d="M 30 35 h 40" stroke-width="6"></path>
                                <path d="M 30 50 h 40" stroke-width="6"></path>
                                <path d="M 30 65 h 40" stroke-width="6"></path>
                            </svg>
                        </a>                        
                </div>
            </div>


            <div class="__content">
"""

    val template4: String = """</div>
        </div>
    </body>
</html>    
"""


    val serialVersionUID : Long = 234L
}