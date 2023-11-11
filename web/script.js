const homePath = `/blog/`;
let cLoc = []
let nav = []
let cAddress = ``

function toggleNavBar() {
    const divider = document.getElementById(`_divider`)
    if (!divider.classList.contains(`_hidden`)) {
        hideNavBar()
    } else {
        const divider = document.getElementById(`_divider`)
        const nBar = document.getElementById(`_theNavBar`)
        nBar.classList.remove(`_hidden`)
        nBar.classList.remove(`_unopaque`)
        nBar.classList.add(`_opaque`)
        void nBar.offsetWidth
        document.getElementById(`_divider`).classList.remove(`_hidden`)

        const toggler = document.getElementById(`_menuToggler`)
        toggler.classList.add(`_hidden`)

    }
}

function hideNavBar() {
    const nBar = document.getElementById(`_theNavBar`)
    nBar.classList.remove(`_opaque`)
    nBar.classList.add(`_unopaque`)
    void nBar.offsetWidth
    nBar.classList.add(`_hidden`)

    const toggler = document.getElementById(`_menuToggler`)
    toggler.classList.remove(`_hidden`)

    document.getElementById(`_divider`).classList.add(`_hidden`)
}

function populateMenu(isFirstLoad) {
    if (isFirstLoad === true) {
        const navStateContainer = document.getElementById(`_navState`)
        const navState = JSON.parse(navStateContainer.textContent)
        nav = navState.nav
        cAddress = navState.address
        if (!nav) return
        cLoc = makeBreadcrumbs(nav, cAddress) 

        if (window.matchMedia(`only screen and (max-width: 800px)`).matches) {
            hideNavBar()
        }
    }

    let cont = document.getElementById(`_theMenu`)
    cont.textContent = ''
    let subAddress = homePath
    let cNode = [[], nav]
    let listPrev = []
    let listOpen = nav
    let indLast = -1
    let nameUp = ''
    let leafMode = false

    for (let i = 0; i < cLoc.length; ++i) {
        cNode = cNode[1][cLoc[i]]
        if (i == cLoc.length - 2) {
            listPrev = cNode[1]
            nameUp = cNode[0]
        } else if (i == cLoc.length - 1) {
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
        linkUp.addEventListener('click', () => moveUp((leafMode === true ? 2 : 1)))
        linkUp.innerHTML = `^ ` + nameUp
        divUp.appendChild(linkUp)
        cont.appendChild(divUp)
    }

    for (let i = 0; i < listOpen.length; ++i) {
        const cParent = document.createElement('div')
        const link = document.createElement('a')
        if (listOpen[i][1].length == 0) {
            if (i == indLast && leafMode === true) {
                let child = document.createElement('div')
                child.style.border = `1px solid hsl(75, 100%, 50%)`
                const displayedName = displayLeaf(listOpen[i][0])
                let par = document.createElement('p')
                par.innerHTML = displayedName
                child.appendChild(par)
                cont.appendChild(child)
            } else {
                link.setAttribute('href', '#')
                link.addEventListener('click', () => goToPage(homePath + listOpen[i][0] + (modeTemp ?
                `?temp` : ``)))
                link.innerHTML = displayLeaf(listOpen[i][0])
            }
        } else {
            link.setAttribute('href', '#')
            link.addEventListener('click', () => (leafMode === true ? strafe(i) : moveDown(i)))
            link.innerHTML = `[` + listOpen[i][0] + `]`
        }
        cParent.appendChild(link)
        cont.appendChild(cParent)
    }
}

function makeBreadcrumbs(navTree, cAddress) {
    const spl = cAddress.split(`/`)
    const result = []
    if (navTree[0] !== `` || navTree[1].length === 0) {
        return result
    }
    var curr = navTree[1]
    for (let i = 0; i < spl.length; i++)  {
        result.push(curr.indexOf(x => x[0] === spl[i]))
        curr = curr[result[i]][1]
    }
    let leafIndex = curr.indexOf(x => x[0] === cAddress)
    if (leafIndex < 0) {
        return []
    }
    result.push(leafIndex)
    return result
}

function goToPage(path) {
    window.location = path;
}

function displayLeaf(leafStr) {
    const splitPath = leafStr.split(`/`);
    const pageName = splitPath[splitPath.length - 1];
    const arrCapitals = [];
    for (let i = 0; i < pageName.length; ++i) {
        if (pageName[i] !== pageName[i].toLowerCase()) arrCapitals.push(i);
    }
    let result = ``;
    if (arrCapitals.length > 0) {
        result = arrCapitals[0] > 0 ? (pageName.substring(0, 1).toUpperCase() +
                                       pageName.substring(1, arrCapitals[0]) + ` `)
                                    : ``;
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
    clearLocation();
    populateMenu(false);
}

function reorderThematic() {
    modeTemp = false
    clearLocation()
    populateMenu(false)
}

function clearLocation() {
    cLoc = []
}


function showLogin() {
    let loginForm = document.getElementById(`_loginDiv`);
    let showButton = document.getElementById(`_loginShow`);
    let hideButton = document.getElementById(`_loginHide`);
    loginForm.style.display = `block`;
    showButton.style.display = `none`;
    hideButton.style.display = `inline`;
}


function hideLogin() {
    let loginForm = document.getElementById(`_loginDiv`);
    let showButton = document.getElementById(`_loginShow`);
    let hideButton = document.getElementById(`_loginHide`);

    loginForm.style.display = `none`;
    showButton.style.display = `inline`;
    hideButton.style.display = `none`;
}


function tryLogin() {
    let userLogin = document.getElementById(`_loginInput`).value;
    let userPw = document.getElementById(`_loginPwInput`).value;
}


document.addEventListener(`DOMContentLoaded`, () => {
    document.getElementById(`_divider`).addEventListener(`click`, toggleNavBar)
    document.getElementById(`_reorderTemporal`).addEventListener(`click`, reorderTemporal)
    document.getElementById(`_reorderThematic`).addEventListener(`click`, reorderThematic)
    document.getElementById(`_menuToggler`).addEventListener(`click`, toggleNavBar)

    populateMenu(true)
});
