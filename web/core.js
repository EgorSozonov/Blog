let cOpen = cLoc[cLoc.length - 1];
let paramTemp = '';
const homePath = "/blog/";

function toggleNavBar() {
    const divider = document.getElementById("__divider")
    if (!divider.classList.contains("__hidden")) {
        hideNavBar()
    } else {
        const divider = document.getElementById("__divider")
        const nBar = document.getElementById("__theNavBar")
        nBar.classList.remove("__hidden")
        nBar.classList.remove("__unopaque")
        nBar.classList.add("__opaque")
        void nBar.offsetWidth
        document.getElementById("__divider").classList.remove("__hidden")

        const toggler = document.getElementById("__menuToggler")
        toggler.classList.add("__hidden")

    }
}

function hideNavBar() {
    const nBar = document.getElementById("__theNavBar")
    nBar.classList.remove("__opaque")
    nBar.classList.add("__unopaque")
    void nBar.offsetWidth
    nBar.classList.add("__hidden")

    const toggler = document.getElementById("__menuToggler")
    toggler.classList.remove("__hidden")

    document.getElementById("__divider").classList.add("__hidden")
}

function populateMenu(isFirstLoad) {
    if (isFirstLoad === true && window.matchMedia("only screen and (max-width: 800px)").matches) {
        hideNavBar()
    }

    const nav = modeTemp ? navTemporal : navTopics;
    if (!nav || !cLoc ) return

    if (modeTemp === true) {
        document.getElementById("__sorterTemp").classList.add("__sortingBorder")
        const sorterOther = document.getElementById("__sorterNom")
        if (sorterOther.classList.contains("__sortingBorder")) sorterOther.classList.remove("__sortingBorder")
    } else {
        document.getElementById("__sorterNom").classList.add("__sortingBorder")
        const sorterOther = document.getElementById("__sorterTemp")
        if (sorterOther.classList.contains("__sortingBorder")) sorterOther.classList.remove("__sortingBorder")
    }

    let cont = document.getElementById("__theMenu")
    cont.textContent = ''
    let subAddress = homePath
    let cNode = [[], nav]
    let listPrev = []
    let listOpen = nav
    let indLast = -1
    let nameUp = ''
    let leafMode = false

    for (let i = 0; i < cLoc.length; ++i) {
        const nextTurn = cLoc[i]

        cNode = cNode[1][nextTurn]
        if (i == cLoc.length - 2) {
            listPrev = cNode[1]
            nameUp = cNode[0]
        }
        if (i == cLoc.length - 1) {
            indLast = cLoc[i]
            leafMode = cNode[1].length === 0
        }
    }
    if (leafMode === true) {
        listOpen = listPrev
    } else {
        listOpen = cNode[1]
        nameUp = cNode[0]
    }

    if (nameUp.length > 0) {
        const divUp = document.createElement('div')
        const linkUp = document.createElement('a')
        linkUp.setAttribute('href', '#')
        linkUp.setAttribute('onclick', 'moveUp(' + (leafMode === true ? 2 : 1) + ');return false;')
        linkUp.innerHTML = "^ " + nameUp
        divUp.appendChild(linkUp)
        cont.appendChild(divUp)
    }

    for (let i = 0; i < listOpen.length; ++i) {
        const cParent = document.createElement('div')
        const link = document.createElement('a')
        if (listOpen[i][1].length == 0) {
            if (i == indLast && leafMode === true) {
                let child = document.createElement('div')
                child.style.border = "1px solid hsl(75, 100%, 50%)"
                const displayedName = displayLeaf(listOpen[i][0])
                let par = document.createElement('p')
                par.innerHTML = displayedName
                child.appendChild(par)
                cont.appendChild(child)
            } else {
                link.setAttribute('href', '#')
                link.setAttribute('onclick', "goToPage('" + homePath + listOpen[i][0] + (modeTemp ?
                "?temp" : "") + "');return false;")
                link.innerHTML = displayLeaf(listOpen[i][0])
            }
        } else {
            link.setAttribute('href', '#')
            link.setAttribute('onclick', (leafMode === true ? 'strafe(' : 'moveDown(') + (i)
                + ');return false;')
            link.innerHTML = "[" + listOpen[i][0] + "]"
        }
        cParent.appendChild(link)
        cont.appendChild(cParent)
    }
}

function goToPage(path) {
    window.location = path;
    toggleNavBar();
}

function displayLeaf(leafStr) {
    const splitPath = leafStr.split("/");
    const pageName = splitPath[splitPath.length - 1];
    const arrCapitals = [];
    for (let i = 0; i < pageName.length; ++i) {
        if (pageName[i] !== pageName[i].toLowerCase()) arrCapitals.push(i);
    }
    let result = "";
    if (arrCapitals.length > 0) {
        for (let j = 1; j < arrCapitals.length; ++j) {
            result = result + pageName.substring(arrCapitals[j - 1], arrCapitals[j]) + ' ';
        }
        result = result + pageName.substring(arrCapitals[arrCapitals.length - 1], pageName.length);
    } else {
        result = pageName;
    }
    return result;
}

function moveUp (times) {
    cLoc.pop();
    if (times > 1 && cLoc.length > 0) cLoc.pop()
    populateMenu(false);
}

function moveDown(indDown) {
    cLoc.push(indDown)
    populateMenu(false)
}

function strafe(indStrafe) {
    if (cLoc.length == 0) {
        return
    }
    cLoc.pop()
    cLoc.push(indStrafe)
    populateMenu(false)
}

function reorderTemporal() {
    modeTemp = true;
    paramTemp = '?o=t';
    clearLocation();
    populateMenu(false);
}

function reorderThematic() {
    modeTemp = false
    paramTemp = ''
    clearLocation()
    populateMenu(false)
}

function clearLocation() {
    cLoc = []
}


function showLogin() {
    let loginForm = document.getElementById("__loginDiv");
    let showButton = document.getElementById("__loginShow");
    let hideButton = document.getElementById("__loginHide");
    loginForm.style.display = 'block';
    showButton.style.display = 'none';
    hideButton.style.display = 'inline';
}


function hideLogin() {
    let loginForm = document.getElementById("__loginDiv");
    let showButton = document.getElementById("__loginShow");
    let hideButton = document.getElementById("__loginHide");

    loginForm.style.display = 'none';
    showButton.style.display = 'inline';
    hideButton.style.display = 'none';
}


function tryLogin() {
    let userLogin = document.getElementById("__loginInput").value;
    let userPw = document.getElementById("__loginPwInput").value;
}


document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("_divider").addEventListener("click", toggleNavBar)
    document.getElementById("_reorderTemporal").addEventListener("click", reorderTemporal)
    document.getElementById("_reorderThematic").addEventListener("click", reorderThematic)

    populateMenu(true)
});