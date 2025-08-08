    (function () {
      const endpoint = window.location.origin + document.querySelector('.people-search-component').getAttribute('data-endpoint');
      const searchInput = document.getElementById('people-search');
      const resultsDiv = document.getElementById('people-results');

      function renderResults(people) {
        if (!people || people.length === 0) {
          resultsDiv.innerHTML = '<p>No results found.</p>';
          return;
        }
        resultsDiv.innerHTML = people.map(person => `
          <div class="person-entry">
            <h3>${person.firstName} ${person.lastName}</h3>
            <p>Email: ${person.emailId}</p>
            <p>Job: ${person.jobType}</p>
            <p>Salary: ${person.salary}</p>
            <p>About: ${person.tellMeAboutYourself?.markdown || ''}</p>
          </div>
        `).join('');
      }

      function fetchPeople(searchText = '') {
        const url = searchText
          ? `${endpoint}/filterListListByName;namefirst=${encodeURIComponent(searchText)}`
          : `${endpoint}/people-list`;

        fetch(url)
          .then(res => res.json())
          .then(data => {
            const people = data?.data?.demoBookList?.items || [];
            renderResults(people);
          })
          .catch(err => {
            console.error('Error fetching GraphQL data:', err);
            resultsDiv.innerHTML = '<p>Error loading data.</p>';
          });
      }

      // Initial fetch
      fetchPeople();

      // Search on input change with debounce
      let debounce;
      searchInput.addEventListener('input', () => {
        clearTimeout(debounce);
        debounce = setTimeout(() => {
          fetchPeople(searchInput.value.trim());
        }, 300);
      });
    })();