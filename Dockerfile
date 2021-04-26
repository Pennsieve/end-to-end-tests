FROM cypress/browsers:chrome69

COPY ./ /e2e/
CMD ["run"]
