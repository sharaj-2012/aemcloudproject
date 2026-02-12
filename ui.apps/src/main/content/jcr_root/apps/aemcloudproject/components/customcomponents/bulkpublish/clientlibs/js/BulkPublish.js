document.addEventListener("DOMContentLoaded", function () {
    const componentPath = document.getElementById("bulkpublish-component").dataset.path;
    const servletUrl = `${componentPath}.bulk.json`;

    function uploadFile() {
        console.log("uploadFile function triggered");
        const fileInput = document.getElementById("uploadedFile");
        const file = fileInput.files[0];
        if (!file) {
            alert("Please select a file.");
            return;
        }

        const formData = new FormData();
        formData.append("uploadedFile", file);

        fetch(servletUrl, {
            method: "POST",
            body: formData
        })
        .then(response => response.text())
        .then(data => {
            document.getElementById("response").innerText = data;
        })
        .catch(error => {
            document.getElementById("response").innerText = "Upload failed: " + error;
        });
    }

    document.getElementById("uploadBtn").addEventListener("click", function () {
        uploadFile();
    });
});