    const componentPath = document.getElementById("reqres-component").dataset.path;
    const servletUrl = `${componentPath}.regres.json`;
        let currentPage = 1;
        function fetchUsers(page) {
            fetch(`${servletUrl}?page=${page}`)
            .then(res => res.json())
            .then(data => {
             const container = document.getElementById("users-container");
                container.innerHTML = data.users.map(user => `
                    <div class="user-card">
                        <img src="${user.avatar}" alt="${user.first_name}" />
                        <h3>${user.first_name} ${user.last_name}</h3>
                        <p>${user.email}</p>
                    </div>
                `).join('');

                document.getElementById("page-label").innerText = `Page ${data.page}`;
                currentPage = data.page;
                document.getElementById("prev-btn").disabled = (currentPage <= 1);
                document.getElementById("next-btn").disabled = (currentPage >= data.total_pages);
            });
        }

        document.getElementById("prev-btn").addEventListener("click", () => {
            if (currentPage > 1) fetchUsers(currentPage - 1);
        });
        document.getElementById("next-btn").addEventListener("click", () => {
            fetchUsers(currentPage + 1);
        });

        fetchUsers(currentPage);